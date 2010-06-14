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
 * Copyright 2003, 2004, 2005, 2006, 2007, 2008, 2009 Peter Baumann /
 rasdaman GmbH.
 *
 * For more information please see <http://www.rasdaman.org>
 * or contact Peter Baumann via <baumann@rasdaman.com>.
 */
package petascope.wcps.server.core;

import petascope.wcps.server.exceptions.InvalidCrsException;
import petascope.wcps.server.exceptions.WCPSException;
import org.w3c.dom.*;

// TODO: Implement class SubsetOperation
public class SubsetOperationCoverageExpr implements IRasNode, ICoverageInfo {

    private IRasNode child;
    private CoverageInfo info = null;

    public SubsetOperationCoverageExpr(Node node, XmlQuery xq)
            throws WCPSException, InvalidCrsException {

        while ((node != null) && node.getNodeName().equals("#text")) {
            node = node.getNextSibling();
        }

        if (node == null) {
            throw new WCPSException("SubsetOperationCoverageExpr parsing error!");
        }

        String nodeName = node.getNodeName();

        System.err.println("SubsetOperationCoverageExpr: node " + nodeName);

        if (nodeName.equals("trim")) {
            child = new TrimCoverageExpr(node, xq);
            info = ((TrimCoverageExpr) child).getCoverageInfo();
        } else if (nodeName.equals("extend")) {
            child = new ExtendCoverageExpr(node, xq);
            info = ((ExtendCoverageExpr) child).getCoverageInfo();
        } else if (nodeName.equals("slice")) {
            child = new SliceCoverageExpr(node, xq);
            info = ((SliceCoverageExpr) child).getCoverageInfo();
        } else {
            throw new WCPSException("Failed to match SubsetOperation: " + nodeName);
        }
    }

    public String toRasQL() {
        return child.toRasQL();
    }

    public CoverageInfo getCoverageInfo() {
        return info;
    }
}