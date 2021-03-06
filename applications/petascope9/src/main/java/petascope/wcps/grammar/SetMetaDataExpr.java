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
package petascope.wcps.grammar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import petascope.util.WCPSConstants;

/**
 * SetMetaDataExpr
 * Creation date: (3/3/2003 2:28:43 AM)
 * @author: mattia parigiani, Sorin Stancu-Mara, Andrei Aiordachioaie
 */
public class SetMetaDataExpr implements IParseTreeNode {
    
    private static Logger log = LoggerFactory.getLogger(SetMetaDataExpr.class);

    CoverageExpr expr;
    String field;
    String function;
    IParseTreeNode param;

    // Set Crs Set
    public SetMetaDataExpr(String op, CoverageExpr expr, CrsList clist) {
        function = op;
        this.expr = expr;
        this.param = clist;
    }

    // Set Null Set
    public SetMetaDataExpr(String op, CoverageExpr expr, RangeExprList param) {
        function = op;
        this.expr = expr;
        this.param = param;
    }

    // Set Identifier
    public SetMetaDataExpr(String op, CoverageExpr expr, String constant) {
        function = op;
        this.expr = expr;
        this.field = constant;
    }

    // Set Interpolation Default
    public SetMetaDataExpr(String op, CoverageExpr expr, InterpolationMethod param,
            String field) {
        function = op;
        this.expr = expr;
        this.param = param;
        this.field = field;
    }

    // Set Interpolation Set
    public SetMetaDataExpr(String op, CoverageExpr expr, InterpolationMethodList param,
            String field) {
        function = op;
        this.expr = expr;
        this.param = param;
        this.field = field;
    }

    public String toXML() {
        String result = "";

        if (function.equalsIgnoreCase(WCPSConstants.MSG_SET_IDENTIFIER)) {
            result += "<" + WCPSConstants.MSG_IDENTIFIER + ">" + field + "</" + WCPSConstants.MSG_IDENTIFIER + ">";
            result += expr.toXML();

            result = "<" + WCPSConstants.MSG_SET_IDENTIFIER + ">" + result + "</" + 
                    WCPSConstants.MSG_SET_IDENTIFIER + ">";
        } else if (function.equalsIgnoreCase(WCPSConstants.MSG_SET_CRSSET)) {
            result += expr.toXML();

            if (param != null) {
                result += param.toXML();
            }

            result = "<" + WCPSConstants.MSG_SET_CRSSET + ">" + result + "</" + WCPSConstants.MSG_SET_CRSSET + ">";
        } else if (function.equalsIgnoreCase(WCPSConstants.MSG_SET_NULL_SET)) {
            result += expr.toXML();

            if (param != null) {
                result += param.toXML();
            }

            result = "<" + WCPSConstants.MSG_SET_NULL_SET + ">" + result + "</" + WCPSConstants.MSG_SET_NULL_SET + ">";
        } else if (function.equalsIgnoreCase(WCPSConstants.MSG_SET_INTERPOLATION_DEFAULT)) {
            result += expr.toXML();
            result += "<" + WCPSConstants.MSG_FIELD + ">" + field + "</" + WCPSConstants.MSG_FIELD + ">";
            result += param.toXML();

            result = "<" + WCPSConstants.MSG_SET_INTERPOLATION_DEFAULT + ">" + result
                    + "</" + WCPSConstants.MSG_SET_INTERPOLATION_DEFAULT + ">";
        } else if (function.equalsIgnoreCase(WCPSConstants.MSG_SET_INTERPOLATION_SET)) {
            result += expr.toXML();
            result += "<" + WCPSConstants.MSG_FIELD + ">" + field + "</" + WCPSConstants.MSG_FIELD + ">";
            result += param.toXML();

            result = "<" + WCPSConstants.MSG_SET_INTERPOLATION_SET + ">" + result + "</" + WCPSConstants.MSG_SET_INTERPOLATION_SET + ">";
        } else {
            log.error(WCPSConstants.ERRTXT_UNKNOWN_SET_METADATA_EXPR+ ": " + function);
        }

        return result;
    }
}
