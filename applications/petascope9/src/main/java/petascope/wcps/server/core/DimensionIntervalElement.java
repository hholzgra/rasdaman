/*
 * This file is part of rasdaman community.
 *
 * Rasdaman community is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rasdaman community is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with rasdaman community.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2003 - 2010 Peter Baumann / rasdaman GmbH.
 *
 * For more information please see <http://www.rasdaman.org>
 * or contact Peter Baumann via <baumann@rasdaman.com>.
 */
package petascope.wcps.server.core;

import java.util.Iterator;
import petascope.exceptions.PetascopeException;
import petascope.core.CoverageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import petascope.exceptions.WCPSException;
import org.w3c.dom.*;
import petascope.exceptions.ExceptionCode;
import petascope.util.CrsUtil;
import petascope.util.WCPSConstants;

/**
 * @author <?>
 * @author <a href="mailto:cmppri@unife.it">Piero Campalani</a>
 */
public class DimensionIntervalElement extends AbstractRasNode implements ICoverageInfo {

    Logger log = LoggerFactory.getLogger(DimensionIntervalElement.class);
    
    private CoverageInfo info = null;
    private AxisName axis;
    private Crs crs;
    private ScalarExpr domain1;
    private ScalarExpr domain2;  // lower and upper bound, or "DomainMetadataExprType" and null
    private long cellCoord1;    // Subsets (after conversion to pixel indices)
    private long cellCoord2;
    private int counter = 0;            // counter for the domain vars
    private CoverageMetadata meta = null;       // metadata about the current coverage
    private boolean finished = false;
    private Node nextNode;
    private boolean transformedCoordinates = false;
    
    /**
     * Constructs an element of a dimension interval.
     * @param node XML Node
     * @param xq WCPS Xml Query object
     * @param covInfo CoverageInfo object about the Trim parent object
     * @throws WCPSException
     */
    public DimensionIntervalElement(Node node, XmlQuery xq, CoverageInfo covInfo)
            throws WCPSException {
        
        while ((node != null) && node.getNodeName().equals("#" + WCPSConstants.MSG_TEXT)) {
            node = node.getNextSibling();
        }
        log.trace(node.getNodeName());
        
        if (covInfo.getCoverageName() != null) {
            // Add Bbox information from coverage metadata, may be useful
            // for converting geo-coordinates to pixel-coordinates
            String coverageName = covInfo.getCoverageName();
            try {
                meta = xq.getMetadataSource().read(coverageName);
            } catch (Exception ex) {
                log.error(ex.getMessage());
                throw new WCPSException(ex.getMessage(), ex);
            }
        }
        
        while (node != null && finished == false) {
            if (node.getNodeName().equals("#" + WCPSConstants.MSG_TEXT)) {
                node = node.getNextSibling();
                continue;
            }
            
            // Try Axis
            try {
                axis = new AxisName(node, xq);
                node = node.getNextSibling();
                continue;
            } catch (WCPSException e) {
            }
            
            // Try CRS name
            try {
                crs = new Crs(node, xq);
                node = node.getNextSibling();
                if (axis == null) {
                    throw new WCPSException(WCPSConstants.ERRTXT_EXPECTED_AXIS_NODE);
                }
                continue;
            } catch (WCPSException e) {
            }
            
            // TODO: how to implement DomainMetadataExpr ?
            
            //            // Try last thing
            //            try
            //            {
            //                domain1 = new DomainMetadataExprType(node, xq);
            //                counter = 1;
            //                continue;
            //            }
            //            catch (WCPSException e)
            //            {
            //                System.err.println("Failed to parse domain metadata!");
            //            }
            
            // Then it must be a pair of nodes "lowerBound" + "upperBound"
            if (node.getNodeName().equals(WCPSConstants.MSG_LOWER_BOUND)) {
                counter = 2;
                domain1 = new ScalarExpr(node.getFirstChild(), xq);
                if (axis == null) {
                    log.error(WCPSConstants.ERRTXT_EXPECTED_AXIS_NODE_LOWERB);
                    throw new WCPSException(WCPSConstants.ERRTXT_EXPECTED_AXIS_NODE_LOWERB);
                }
            } else if (node.getNodeName().equals(WCPSConstants.MSG_UPPER_BOUND)) {
                counter = 2;
                domain2 = new ScalarExpr(node.getFirstChild(), xq);
                if (axis == null) {
                    log.error(WCPSConstants.ERRTXT_EXPECTED_AXIS_NODE_UPPERB);
                    throw new WCPSException(WCPSConstants.ERRTXT_EXPECTED_AXIS_NODE_UPPERB);
                }
            } else {
                log.error("  " + WCPSConstants.ERRTXT_UNEXPETCTED_NODE + ": " + node.getFirstChild().getNodeName());
                throw new WCPSException(WCPSConstants.ERRTXT_UNEXPETCTED_NODE + ": " + node.getFirstChild().getNodeName());
            }
            
            if (axis != null && counter == 1 && domain1 != null) {
                finished = true;
            }
            if (axis != null && counter == 2 && domain1 != null && domain2 != null) {
                finished = true;
            }
            
            if (finished == true) {
                nextNode = node.getNextSibling();
            }
            
            node = node.getNextSibling();
        }
        
        if (crs == null) {
            // if no CRS is specified assume native CRS -- DM 2012-mar-05
            String axisName = axis.toRasQL();
            
            DomainElement axisDomain = meta.getDomainByName(axisName);
            if (axisDomain != null) {
                String crsName = axisDomain.getCrs();
                log.info("  Using native CRS: " + crsName);
                crs = new Crs(crsName);
                
                if (crsName == null) {
                    log.warn("  No native CRS specified for axis " + axisName + ", assuming pixel coordinates.");
                    crs = new Crs(CrsUtil.GRID_CRS);
                }
            } else {
                log.warn(WCPSConstants.WARNTXT_NO_NATIVE_CRS_P1 + " " + axisName + WCPSConstants.WARNTXT_NO_NATIVE_CRS_P2);
                crs = new Crs(CrsUtil.GRID_CRS);
                this.transformedCoordinates = true;

                //log.error(axisName + " is not available for coverage " + meta.getCoverageName());
                //throw new WCPSException(ExceptionCode.InvalidRequest, 
                //        axisName + " is not available for coverage " + meta.getCoverageName());
            }
        }
        
        if (finished == true && !crs.getName().equals(CrsUtil.GRID_CRS)) {
            convertToPixelCoordinates();
        }
    }
    
    /* If input coordinates are geo-, convert them to pixel coordinates. */
    private void convertToPixelCoordinates() throws WCPSException {

        if (meta.getBbox() == null && crs != null) {
            throw new RuntimeException(WCPSConstants.MSG_COVERAGE + " '" + meta.getCoverageName()
                    + "' " + WCPSConstants.ERRTXT_IS_NOT_GEOREFERENCED);
        }
        if (counter == 2 && crs != null && domain1.isSingleValue() && domain2.isSingleValue()) {
            log.debug("[Transformed] requested subsettingCrs is '{}', should match now native CRS '{}'",
                    crs.getName(), meta.getBbox().getCrsName());
            try {
                this.transformedCoordinates = true;
                // Convert to pixel coordinates
                String val1 = domain1.getSingleValue();
                String val2 = domain2.getSingleValue();
                boolean dom1IsNum = !domain1.isStringScalarExpr();
                boolean dom2IsNum = !domain2.isStringScalarExpr();
                String axisName = axis.toRasQL();
                int[] pCoord = crs.convertToPixelIndices(meta, axisName, val1, dom1IsNum, val2, dom2IsNum);
                cellCoord1 = pCoord[0];
                cellCoord2 = pCoord[1];
            } catch (PetascopeException e) {
                this.transformedCoordinates = false;
                throw new WCPSException(ExceptionCode.InvalidMetadata,
                        WCPSConstants.ERRTXT_ERROR_WHILE_CONVERTING);            }
        }
    }
    
    /* Not used */
    @Override
    public String toRasQL() {
        return WCPSConstants.MSG_DOMAIN_INTERVAL_ELEMENT_NOT;
    }
    
    @Override
    public CoverageInfo getCoverageInfo() {
        return info;
    }
    
    public Node getNextNode() {
        return nextNode;
    }
    
    public String getAxisName() {
        return this.axis.toRasQL();
    }
    
    public String getAxisCoords() {
        return this.domain1.toRasQL() + " : " + this.domain2.toRasQL();
    }
    
    public String getLowCoord() {
        if (transformedCoordinates) {
            return String.valueOf(cellCoord1);
        } else {
            return this.domain1.toRasQL();
        }
    }
    
    public String getHighCoord() {
        if (transformedCoordinates) {
            return String.valueOf(cellCoord2);
        } else {
            return this.domain2.toRasQL();
        }
    }
}
