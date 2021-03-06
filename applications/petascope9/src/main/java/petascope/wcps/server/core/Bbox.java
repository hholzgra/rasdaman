/*
 * This file is part of rasdaman community.
 *
 * Rasdaman community is free software: you can redistribute it and/or modify
 * it under the terms of the GNU  General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rasdaman community is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU  General Public License for more details.
 *
 * You should have received a copy of the GNU  General Public License
 * along with rasdaman community.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2003 - 2010 Peter Baumann / rasdaman GmbH.
 *
 * For more information please see <http://www.rasdaman.org>
 * or contact Peter Baumann via <baumann@rasdaman.com>.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package petascope.wcps.server.core;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import petascope.exceptions.ExceptionCode;
import petascope.exceptions.WCPSException;
import petascope.exceptions.WCSException;
import petascope.util.CrsUtil;
import petascope.util.AxisTypes;
import petascope.exceptions.PetascopeException;
import petascope.util.WCPSConstants;
import petascope.util.TimeUtil;

/** 
 * NOTE: the WGS84 bounding needs to take care to transform only the /spatial/ axes,
 * whereas the other extents don't have to change.
 * 
 * @author <a href="mailto:cmppri@unife.it">Piero Campalani</a>
 */
public class Bbox implements Cloneable {
    
    private static Logger log = LoggerFactory.getLogger(Bbox.class);
 
    private String crsName;
    private String coverageName;
    private List<String> minValues;
    private List<String> maxValues;
    private List<String> types;
    private List<String> names; 
    private Double wgs84minLon;
    private Double wgs84maxLon;
    private Double wgs84minLat;
    private Double wgs84maxLat;
    private Boolean hasWgs84Bbox = false;
    private List<DomainElement> domains; // Cloning
    
    public Bbox(String crs, List<DomainElement> domains, String coverage) throws WCPSException, WCSException, PetascopeException {
        
        this.domains = new ArrayList();
        this.domains.addAll(domains);
        coverageName = coverage;
        minValues = new ArrayList<String>();
        maxValues = new ArrayList<String>();
        types     = new ArrayList<String>();
        names     = new ArrayList<String>();
        
        // Raw Bounding-box
        for (DomainElement el : domains) {
            if ((el.getMinValue() == null) || (el.getMaxValue() == null)) {
            throw new WCPSException(ExceptionCode.InvalidMetadata,
                    WCPSConstants.ERRTXT_INVALID_BOUNDING_BOX);
            }            
            minValues.add(el.getMinValue());
            maxValues.add(el.getMaxValue());
            types.add(el.getType());
            names.add(el.getName());
        }
        
        if (crs == null) {
            throw new WCPSException(ExceptionCode.InvalidMetadata, WCPSConstants.ERRTXT_INVALID_CRS);
        } else {
            crsName = crs;
        }        
        
        /* Get WGS84 bounding box if the non-WGS84 coverage has X and Y axis from which to convert.
         * (NOTE1) Simple copy of coordinates for WGS84 coverages: Capabilities response to list, 
         * though redundant, the Wgs84Bbox of WGS84 coverages as well (uniformity).
         * (NOTE2) Keep WGS84 bbox for planetary CRSs as well? It can be useful, but they need
         * a special treatment since the CRS must be loaded via WKT, URI is unknown for GeoTools.
         */
        double lowX=0D, lowY=0D, highX=0D, highY=0D;
        String crsSourceX="", crsSourceY="";
        for (DomainElement el : domains) { 
            // X AXIS
            if (el.getType().equals(AxisTypes.X_AXIS)) {
                crsSourceX = el.getCrs();
                if (CrsUtil.CrsUri.areEquivalent(crsSourceX, CrsUtil.CrsUri(CrsUtil.EPSG_AUTH, CrsUtil.WGS84_EPSG_CODE))) {
                    wgs84minLon = Double.parseDouble(el.getMinValue());
                    wgs84maxLon = Double.parseDouble(el.getMaxValue());
                } else {
                    lowX  = Double.parseDouble(el.getMinValue());
                    highX = Double.parseDouble(el.getMaxValue());
                }
            // Y AXIS
            } else if (el.getType().equals(AxisTypes.Y_AXIS)) {
                crsSourceY = el.getCrs();
                if (CrsUtil.CrsUri.areEquivalent(crsSourceY, CrsUtil.CrsUri(CrsUtil.EPSG_AUTH, CrsUtil.WGS84_EPSG_CODE))) {
                    wgs84minLat = Double.parseDouble(el.getMinValue());
                    wgs84maxLat = Double.parseDouble(el.getMaxValue());
                } else {
                    lowY  = Double.parseDouble(el.getMinValue());
                    highY = Double.parseDouble(el.getMaxValue());
                }
            }
        } 
        
        // Consistency check
        if (!crsSourceX.isEmpty() && !crsSourceY.isEmpty()) {
            if (!CrsUtil.CrsUri.areEquivalent(crsSourceX, crsSourceY)) {
                throw new WCPSException(ExceptionCode.InvalidMetadata,
                        "Invalid bounding box: X and Y axis have different CRS:" + crsSourceX + "<->" + crsSourceY);
            }
            
            // Now crsSourceX/crsSourceY are tested to be the same: check 
            if (!CrsUtil.CrsUri.areEquivalent(crsSourceX, CrsUtil.CrsUri(CrsUtil.EPSG_AUTH, CrsUtil.WGS84_EPSG_CODE)) // --> no transform needed: WGS84 values stored above
                    && domains.size() == 2                                      // --> CRS is 2D and spatial
                    && getSpatialDimensionality() == 2                          //
                    && CrsUtil.CrsUri.getAuthority(crsSourceX).equals(CrsUtil.EPSG_AUTH)) {  // CRS is EPSG
                try {
                    CrsUtil crsTool   = new CrsUtil(crsSourceX, CrsUtil.CrsUri(CrsUtil.EPSG_AUTH, CrsUtil.WGS84_EPSG_CODE));
                    List<Double> temp = crsTool.transform(new double[] {lowX, lowY, highX, highY});
                    wgs84minLon = temp.get(0);
                    wgs84minLat = temp.get(1);
                    wgs84maxLon = temp.get(2);
                    wgs84maxLat = temp.get(3);
                } catch (WCSException e) {
                    log.warn("Error while getting WGS84 bounding box of coverage " + coverageName);
                    log.trace(e.getMessage());
                }
                hasWgs84Bbox = true;
            } else if (CrsUtil.CrsUri.areEquivalent(crsSourceX, CrsUtil.CrsUri(CrsUtil.EPSG_AUTH, CrsUtil.WGS84_EPSG_CODE))) {
                // Coverage *is* a WGS84 coverage (no transform needed)
                hasWgs84Bbox = true;
            } else {
                // Spatial bbox is non-EPSG or domain does not comprise spatial axes.
                // TODO: manage **planetary** spatial axes.
            }
        }
        
        log.trace(toString());
    }

    @Override
    public Bbox clone() {
        try {
            return new Bbox(crsName, domains, coverageName);
        } catch (WCPSException e) {
            log.warn(e.getMessage());
            return null;
        } catch (WCSException e) {
            log.warn(e.getMessage());
            return null;
        } catch (PetascopeException e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    @Override
    public String toString() {
        String extents = "";
        for (int i=0; i<minValues.size(); i++) {
            if (i > 0) extents += ", ";
            extents += types.get(i) + "(" + getMinValue(i) + ", " + getMaxValue(i) + ")";
        }        
        return WCPSConstants.MSG_CRS_C + " '" + getCrsName() + "' { " + WCPSConstants.MSG_BOUNDING_BOX + " [" + extents + "] }";
    }

    /**
     * @return the minValues of a specified axis.
     */
    public String getMinValue(String axisName) {
        for (int i=0; i<names.size(); i++) { 
            if (names.get(i).equals(axisName)) {
                return getMinValue(i);
            }
        }
        return null;
    }    
    public String getMinValue(int index) {
        return minValues.get(index);
    }
    
    /**
     * @return the maxValue of a specified axis.
     */
    public String getMaxValue(String axisName) {
        for (int i=0; i<names.size(); i++) { 
            if (names.get(i).equals(axisName)) {
                return getMaxValue(i);
            }
        }
        return null;
    }    
    public String getMaxValue(int index) {
        return maxValues.get(index);
    }
    
    /**
     * Returns the i-order of an axis.
     * @param axisName
     * @return the i-order of axisName
     */
    public Integer getIndex(String axisName) {
        for (int i=0; i<names.size(); i++) {
            if (names.get(i).equals(axisName)) {
                return i;
            }
        }
        return null;
    }
    
    /**
     * @return the CRS name
     */
    public String getCrsName() {
        return crsName;
    }
    
    /**
     * @return the CRS name where the spatial CRS is replaced by WGS84 URI.
     */
    public String getWgs84CrsName() {
        LinkedHashSet<String> crsUris; // avoid CRS duplication if some axes share the CRS; keep order.
        if (CrsUtil.CrsUri.isCompound(crsName)) {
            // Extract the involved atomic CRSs:
            /* Assumption: suppose that CRS involving height are 2D+1D, not 3D: still not considering
             * 3D->WGS84 transforms.
             */
            crsUris = new LinkedHashSet<String>();
            for (DomainElement dom : domains) {
                if (dom.getType().equals(AxisTypes.X_AXIS) ||
                        dom.getType().equals(AxisTypes.Y_AXIS)) {                   
                    crsUris.add(CrsUtil.CrsUri(CrsUtil.EPSG_AUTH, CrsUtil.WGS84_EPSG_CODE));
                } else
                    crsUris.add(dom.getCrs());
            }
            
            // Build the compound CRS:
            String ccrs = CrsUtil.CrsUri.createCompound(crsUris);
            return ccrs;
            
        } else
            return hasWgs84Bbox ? CrsUtil.CrsUri(CrsUtil.EPSG_AUTH, CrsUtil.WGS84_EPSG_CODE) : "";
    }
    
    /**
     * @return the coverage name
     */
    public String getCoverageName() {
        return coverageName;
    }
    
    /**
     * @return The lower-corner longitude of the WGS bounding box.
     */
    public Double getWgs84minLon() {
        return wgs84minLon;
    }

    /**
     * @return The upper-corner longitude of the WGS bounding box.
     */
    public Double getWgs84maxLon() {
        return wgs84maxLon;
    }

    /**
     * @return The lower-corner latitude of the WGS bounding box.
     */
    public Double getWgs84minLat() {
        return wgs84minLat;
    }

    /**
     * @return The upper-corner latitude of the WGS bounding box.
     */
    public Double getWgs84maxLat() {
        return wgs84maxLat;
    }
    
   /**
     * @return Whether a WGS84 bounding box has been computed for this object.
     */
    public Boolean hasWgs84Bbox() {
        return hasWgs84Bbox;
    }
    
    /**
     * @return The dimensionality of the bounding box (namely of the coverage).
     */
    public int getDimensionality() {
        return domains.size();
    }
    
    /**
     * @return The number of the spatial dimensions in the bounding box (for CRS transform purposes)
     */
    private int getSpatialDimensionality() {
        int counter = 0;
        for (DomainElement domain : domains) {
            if (domain.getType().equals(AxisTypes.X_AXIS)
                    || domain.getType().equals(AxisTypes.Y_AXIS)
                    || domain.getType().equals(AxisTypes.ELEV_AXIS))
                counter += 1;
        }
        return counter;
    }
    
    /**
     * @return The type of the specified axis of the bounding box.
     */
    public String getType(int i) {
        return domains.get(i).getType();
    }
    
    /**
     * XML-related utilities: list the bbox corners, separated each other with a white space.
     * For WGS84 corners: if X or Y domains then put the WGS84 value, otherwise leave it as is.
     * @return The String of concatenated corner coordinates.
     * NOTE: redundant with GetCoverageMetadata fields, but here can manage WGS84 equivalent corners.
     */
    public String getLowerCorner() {
        String output = "";
        String tmp;
        // Loop through the N dimensions
        for (int i = 0; i < getDimensionality(); i++) {
            if (i>0) output += " ";
            tmp = getMinValue(i);
            // Fill possible space between date and time in timestamp with "T" (ISO:8601)
            // Disable: breaks XML schema (requires xs:double)
            //if (getType(i).equals(AxisTypes.T_AXIS)) {
            //    tmp = tmp.replaceFirst(" ", TimeUtil.ISO8601_T_KEY);
            //}
            output += tmp;
        }
        return output;
    }
    // Used when get the corner only for a subset of the whole available axes (eg. gml:origin after slicing)
    public String getLowerCorner(String[] axisLabels) {
        String output = "";
        String tmp;
        // Loop through the N dimensions
        for (int i = 0; i < axisLabels.length; i++) {
            if (i>0) output += " ";
            tmp = getMinValue(axisLabels[i]);
            // Fill possible space between date and time in timestamp with "T" (ISO:8601)
            // Disable: breaks XML schema (requires xs:double)
            //if (getType(i).equals(AxisTypes.T_AXIS)) {
            //    tmp = tmp.replaceFirst(" ", TimeUtil.ISO8601_T_KEY);
            //}
            output += tmp;
        }
        return output;
    }
    public String getWgs84LowerCorner() {
        String output = "";
        String tmp;
        // Loop through the N dimensions
        for (int i = 0; i < getDimensionality(); i++) {
            if (i>0) output += " ";
            if (getType(i).equals(AxisTypes.X_AXIS)) output += getWgs84minLon(); else
            if (getType(i).equals(AxisTypes.Y_AXIS)) output += getWgs84minLat(); 
            else {
                tmp = getMinValue(i);
                // Fill possible space between date and time in timestamp with "T" (ISO:8601)
                // Disable: breaks XML schema (requires xs:double)
                //if (getType(i).equals(AxisTypes.T_AXIS))
                //    tmp = tmp.replaceFirst(" ", TimeUtil.ISO8601_T_KEY);
                output += tmp;
            }
        }
        return output;
    }        
    public String getUpperCorner() {
        String output = "";
        String tmp;
        // Loop through the N dimensions
        for (int i = 0; i < getDimensionality(); i++) {
            if (i>0) output += " ";
            tmp = getMaxValue(i);
            // Fill possible space between date and time in timestamp with "T" (ISO:8601)
            // Disable: breaks XML schema (requires xs:double)
            //if (getType(i).equals(AxisTypes.T_AXIS))
            //    tmp = tmp.replaceFirst(" ", TimeUtil.ISO8601_T_KEY);
            output += tmp;
        }
        return output;
    }
    // Used when get the corner only for a subset of the whole available axes 
    public String getUpperCorner(String[] axisLabels) {
        String output = "";
        String tmp;
        // Loop through the N dimensions
        for (int i = 0; i < axisLabels.length; i++) {
            if (i>0) output += " ";
            tmp = getMaxValue(axisLabels[i]);
            // Fill possible space between date and time in timestamp with "T" (ISO:8601)
            // Disable: breaks XML schema (requires xs:double)
            //if (getType(i).equals(AxisTypes.T_AXIS)) {
            //    tmp = tmp.replaceFirst(" ", TimeUtil.ISO8601_T_KEY);
            //}
            output += tmp;
        }
        return output;
    }
    public String getWgs84UpperCorner() {
        String output = "";
        String tmp;
        // Loop through the N dimensions
        for (int i = 0; i < getDimensionality(); i++) {
            if (i>0) output += " ";
            if (getType(i).equals(AxisTypes.X_AXIS)) output += getWgs84maxLon(); else
            if (getType(i).equals(AxisTypes.Y_AXIS)) output += getWgs84maxLat(); 
            else {
                tmp = getMaxValue(i);
                // Fill possible space between date and time in timestamp with "T" (ISO:8601)
                // Disable: breaks XML schema (requires xs:double)
                //if (getType(i).equals(AxisTypes.T_AXIS))
                //    tmp = tmp.replaceFirst(" ", TimeUtil.ISO8601_T_KEY);
                output += tmp;
            }
        }
        return output;
    }        
}
