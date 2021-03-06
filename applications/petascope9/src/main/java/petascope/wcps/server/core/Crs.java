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

import java.math.BigDecimal;
import java.math.RoundingMode;
import petascope.core.CoverageMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import petascope.core.DbMetadataSource;
import petascope.core.DynamicMetadataSource;
import petascope.core.IDynamicMetadataSource;
import petascope.exceptions.ExceptionCode;
import petascope.exceptions.PetascopeException;
import petascope.exceptions.WCPSException;
import petascope.util.AxisTypes;
import petascope.util.WCPSConstants;
import petascope.util.CrsUtil;
import petascope.util.TimeUtil;

public class Crs extends AbstractRasNode {

    private static final Logger log = LoggerFactory.getLogger(Crs.class);
    private String crsName;
    private DbMetadataSource dbMeta;
    
    public Crs(String srsName) {
        crsName = srsName;
    }
    
    public Crs(Node node, XmlQuery xq) throws WCPSException {
        while ((node != null) && node.getNodeName().equals("#" + WCPSConstants.MSG_TEXT)) {
            node = node.getNextSibling();
        }
        log.trace(node.getNodeName());
        
        if (node != null && node.getNodeName().equals(WCPSConstants.MSG_SRS_NAME)) {
            String val = node.getTextContent();
            this.crsName = val;
            //if (crsName.equals(DomainElement.IMAGE_CRS) || crsName.equals(DomainElement.WGS84_CRS)) {
            log.trace(WCPSConstants.MSG_FOUND_CRS + ": " + crsName);
            //} else {
            //    throw new WCPSException("Invalid CRS: '" + crsName + "'");
            //}
        } else {
            throw new WCPSException(WCPSConstants.ERRTXT_COULD_NOT_FIND_SRSNAME);
        }
        
        // If coverage is not dynamic, it can be irregular: I need to query the DB to convert to pixels.
        IDynamicMetadataSource dmeta = xq.getMetadataSource();
        if (dmeta instanceof DynamicMetadataSource &&
                ((DynamicMetadataSource)dmeta).getMetadataSource() instanceof DbMetadataSource) {
            dbMeta = (DbMetadataSource) ((DynamicMetadataSource)dmeta).getMetadataSource();
        }
    }
    
    /**
     * Converts an interval subset to CRS:1 domain (grid indices).
     * @param covMeta       Metadata of the coverage
     * @param axisName      The axis label of the subset
     * @param stringLo      The lower bound of the subset
     * @param loIsNumeric   True if the bound is a numeric value (otherwise timestamp)
     * @param stringHi      The upper bound of the subset
     * @param hiIsNumeric   True if the bound is a numeric value (otherwise timestamp)
     * @return              The pixel indices corresponding to this subset
     * @throws WCPSException 
     */
    public static int[] convertToPixelIndices(CoverageMetadata covMeta, String axisName,
            String stringLo, boolean loIsNumeric, String stringHi, boolean hiIsNumeric)
            throws PetascopeException {
        
        int[] out = new int[2];
        
        // IMPORTANT: y axis are decreasing wrt pixel domain
        // TODO: generalize behaviour by assigning a direction to each axis (e.g. in the DomainElement object).
        boolean zeroIsMin = !axisName.equals(AxisTypes.Y_AXIS);
        
        DomainElement      dom = covMeta.getDomainByName(axisName);
        CellDomainElement cdom = covMeta.getCellDomainByName(axisName);        
        
        // bool to differentiate between a timestamp and a numeric subset
        boolean subsetWithTimestamps = dom.getType().equals(AxisTypes.T_AXIS) && !loIsNumeric;
        String datumOrigin = dom.getAxisDef().getCrsDefinition().getDatumOrigin(); // can also be null if !temporal axis: use axisType to guard
        String axisUoM     = dom.getUom();
        
        // Inconsistency check
        if (cdom == null || dom == null) {
            log.error(WCPSConstants.ERRTXT_COULD_NOT_FIND_COVERAGE_P1 + axisName + WCPSConstants.ERRTXT_COULD_NOT_FIND_COVERAGE_P2 + ": " + covMeta.getCoverageName());
            throw new PetascopeException(ExceptionCode.NoApplicableCode, WCPSConstants.ERRTXT_COULD_NOT_FIND_COVERAGE_P1 + axisName + WCPSConstants.ERRTXT_COULD_NOT_FIND_COVERAGE_P2 + ":" + covMeta.getCoverageName());
        }
        
        // Get cellDomain extremes
        int pxLo = cdom.getLo().intValue();
        int pxHi = cdom.getHi().intValue();
        log.trace(WCPSConstants.MSG_CELL_DOMAIN_EXTREMES + pxLo + ", " + WCPSConstants.MSG_HIGH_U + ":" + pxHi);
        
        // Get Domain extremes (real sdom)
        String domLo = dom.getMinValue();
        String domHi = dom.getMaxValue();
        log.trace("Domain extremes coordinates: (" + domLo + ", " + domHi + ")");
        log.trace("Subset cooordinates: (" + stringLo + ", " + stringHi + ")");
        
        /*---------------------------------*/
        /*         VALIDITY CHECKS         */
        /*---------------------------------*/
        log.trace("Checking order, format and bounds of axis {} ...", axisName);

        // Requires homogeneity in the bounds of a subset
        if (loIsNumeric^hiIsNumeric) { // XOR
            log.error("(" + stringLo + "," + stringHi + ") subset is invalid.");
            throw new PetascopeException(ExceptionCode.InvalidRequest,
                    "(" + stringLo + "," + stringHi + ") subset requires bounds of the same domain.");
        }
        // (From now on, test can be made on a single bound)        
        
        // If subsets are not numeric, than the axis must be temporal (String implies timestamp, currently)
        if (!loIsNumeric && !dom.getType().equals(AxisTypes.T_AXIS)) {
            log.error("(" + stringLo + "," + stringHi + ") subset is invalid.");
            throw new PetascopeException(ExceptionCode.InvalidRequest, 
                    "Axis " + axisName + " requires numeric subsets.");
        }
        
        // Check order of subset: separate treatment for temporal axis with timestamps subsets
        if (subsetWithTimestamps) {            
            // Check order
            if (!TimeUtil.isOrderedTimeSubset(stringLo, stringHi)) {
                throw new PetascopeException(ExceptionCode.InvalidSubsetting,
                        axisName + " axis: lower bound " + stringLo + " is greater then the upper bound " + stringHi);
            }            
        } else {
            
            // Numerical axis:
            double coordLo = Double.parseDouble(stringLo);
            double coordHi = Double.parseDouble(stringHi);
            
            // Check order
            if (coordHi < coordLo) {
                throw new PetascopeException(ExceptionCode.InvalidSubsetting,
                        axisName + " axis: lower bound " + coordLo + " is greater the upper bound " + coordHi);
            }
            
            // Check intersection with extents
            if (dom.getUom().equals(CrsUtil.PIXEL_UOM)) {
                // Pixel-domain axis: get info from cellDomain (sdom) directly.
                if (coordLo > pxHi || coordHi < pxLo) {
                    throw new PetascopeException(ExceptionCode.InvalidSubsetting,
                            axisName + " axis: subset (" + coordLo + ":" + coordHi + ") is out of bounds.");
                }
            } else {
                if (coordLo > Double.parseDouble(domHi) || coordHi < Double.parseDouble(domLo)) {
                    throw new PetascopeException(ExceptionCode.InvalidSubsetting,
                            axisName + " axis: subset (" + coordLo + ":" + coordHi + ") is out of bounds.");
                }
            }
        }
        
        /*---------------------------------*/
        /*             CONVERT             */
        /*---------------------------------*/
        log.trace("Converting axis {} interval to pixel indices ...", axisName);
        // There can be several different cases depending on spacing and type of this axis:
        if (dom.isIrregular()) {
            
            // Consistency check
            // TODO need to find a way to solve `static` issue of dbMeta
            if (dbMeta == null) {
                throw new PetascopeException(ExceptionCode.InternalComponentError,
                        "Axis " + axisName + " is irregular but is not linked to DbMetadataSource.");
            }
            
            // Need to query the database (IRRSERIES table) to get the extents
            try {
                String numLo = stringLo;
                String numHi = stringHi;
                
                if (subsetWithTimestamps) {
                    // Need to convert timestamps to TemporalCRS numeric coordinates
                    numLo = "" + TimeUtil.countPixels(datumOrigin, stringLo, axisUoM);
                    numHi = "" + TimeUtil.countPixels(datumOrigin, stringHi, axisUoM);
                }
            
                // Retrieve correspondent cell indexes (unique method for numerical/timestamp values)
                // TODO: I need to extract all the values, not just the extremes
                out = dbMeta.getCellFromIrregularAxis(
                        covMeta.getCoverageName(),
                        covMeta.getDomainIndexByName(axisName), // i-order of axis
                        numLo, numHi, 
                        DbMetadataSource.TYPE_FLOAT8);
                
            } catch (WCPSException e) {
                throw e;
            } catch (Exception e) {
                throw new PetascopeException(ExceptionCode.InternalComponentError,
                        "Error while fetching cell boundaries of irregular axis " +
                        axisName + "of coverage " + covMeta.getCoverageName() + ".");
            }
            
        } else {
            // The axis is regular: need to differentiate between numeric and timestamps
            if (subsetWithTimestamps) {
                // Need to convert timestamps to TemporalCRS numeric coordinates
                int numLo = TimeUtil.countPixels(datumOrigin, stringLo, axisUoM);
                int numHi = TimeUtil.countPixels(datumOrigin, stringHi, axisUoM);                
                        
                // Consistency check
                if (numHi < Double.parseDouble(domLo) || numLo > Double.parseDouble(domHi)) {
                    throw new PetascopeException(ExceptionCode.InternalComponentError,
                            "Translated pixel indixes of regular temporal axis (" + 
                            numLo + ":" + numHi +") exceed the allowed values.");
                }
                
                // Replace timestamps with numeric subsets
                stringLo = "" + numLo;
                stringHi = "" + numHi;
                // Now subsets can be tranlsated to pixels with normal numeric proportion                
            }
            
            
            /* Loop to get the pixel subset values.
             * Different cases (0%-overlap subsets are excluded by the checks above)
             *        MIN                                                     MAX
             *         |-------------+-------------+-------------+-------------|
             *              cell0         cell1         cell2         cell3
             * i)   |_____________________|
             * ii)                              |__________________|
             * iii)                                                         |_________________|
             */
            // Numeric interval: simple mathematical proportion
            double coordLo = Double.parseDouble(stringLo);
            double coordHi = Double.parseDouble(stringHi);
            
            // Numerical domain extremes (real sdom)
            double dDomLo = Double.parseDouble(domLo);
            double dDomHi = Double.parseDouble(domHi);
            
            // Get cell dimension -- Use BigDecimals to avoid finite arithemtic rounding issues of Doubles
            double cellWidth = (
                    BigDecimal.valueOf(dDomHi).subtract(BigDecimal.valueOf(dDomLo)))
                    .divide((BigDecimal.valueOf(pxHi+1)).subtract(BigDecimal.valueOf(pxLo)), RoundingMode.UP)
                    .doubleValue();
            //      = (dDomHi-dDomLo)/(double)((pxHi-pxLo)+1);
            
            // Open interval on the right: take away epsilon from upper bound:
            //double coordHiEps = coordHi - cellWidth/10000;    // [a,b) subsets
            double coordHiEps = coordHi;                        // [a,b] subsets
            
            // Conversion to pixel domain
            /*
             * Policy = minimum encompassing BoundingBox returned (of course)
             *
             *     9°       10°       11°       12°       13°
             *     o---------o---------o---------o---------o
             *     |  cell0  |  cell1  |  cell2  |  cell3  |
             *     [=== s1 ==]
             *          [=== s2 ==]
             *           [===== s3 ====]
             *      [= s4 =]
             *
             * -- [a,b] closed intervals:
             * s1(9°  ,10°  ) -->  [cell0:cell1]
             * s2(9.5°,10.5°) -->  [cell0:cell1]
             * s3(9.7°,11°  ) -->  [cell0:cell2]
             * s4(9.2°,9.8° ) -->  [cell0:cell0]
             *
             * -- [a,b) open intervals:
             * s1(9°  ,10°  ) -->  [cell0:cell0]
             * s2(9.5°,10.5°) -->  [cell0:cell1]
             * s3(9.7°,11°  ) -->  [cell0:cell1]
             * s4(9.2°,9.8° ) -->  [cell0:cell0]
             */
            if (dom.getUom().equals(CrsUtil.PIXEL_UOM)) {
                // Gridspace referenced image: subsets *are* pixels
                out = new int[] { (int)coordLo, (int)coordHi };
            } else if (zeroIsMin) {
                // Normal linear numerical axis
                out = new int[] {
                    (int)Math.floor((coordLo    - dDomLo) / cellWidth) + pxLo,
                    (int)Math.floor((coordHiEps - dDomLo) / cellWidth) + pxLo
                };
            } else {
                // Linear negative axis (eg northing of georeferenced images)
                out = new int[] {
                    // First coordHi, so that left-hand index is the lower one
                    (int)Math.ceil((dDomHi - coordHiEps) / cellWidth) + pxLo,
                    (int)Math.ceil((dDomHi - coordLo)    / cellWidth) + pxLo
                };
            }
            log.debug("Transformed coords indices (" + out[0] + "," + out[1] + ")");
            
            // Check outside bounds:
            out[0] = (out[0]<pxLo) ? pxLo : ((out[0]>pxHi)?pxHi:out[0]);
            out[1] = (out[1]<pxLo) ? pxLo : ((out[1]>pxHi)?pxHi:out[1]);
            log.debug("Transformed rebounded coords indices (" + out[0] + "," + out[1] + ")");
        }
        return out;
    }
    // Dummy overload (for DimensionPointElements)
    public int convertToPixelIndices(CoverageMetadata meta, String axisName, String value, boolean isNumeric) throws PetascopeException {
        return convertToPixelIndices(meta, axisName, value, isNumeric, value, isNumeric)[0];
    }
    
    @Override
    public String toRasQL() {
        return crsName;
    }
    
    public String getName() {
        return crsName;
    }
}
