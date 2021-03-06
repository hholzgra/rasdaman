/*
 * This file is part of rasdaman community.
 *
 * Rasdaman community is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rasdaman community is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with rasdaman community.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2003 - 2012 Peter Baumann / rasdaman GmbH.
 *
 * For more information please see <http://www.rasdaman.org>
 * or contact Peter Baumann via <baumann@rasdaman.com>.
 */
package secore;

import secore.util.Config;
import secore.util.Pair;
import secore.util.SecoreException;
import secore.util.ExceptionCode;
import java.util.ArrayList;
import java.util.List;
import static secore.util.Constants.*;
import static secore.GeneralHandler.*;

/**
 * Wrapper for resolving requests.
 *
 * @author Dimitar Misev
 */
public class ResolveRequest {
  
  private final List<Pair<String, String>> params;
  private final String operation;
  private final String service;
  private final String fulUri;
  private int expand;
  
  /**
   * Create a new request to resolve. Note: the service URL specified
   * in the configuration is used.
   * 
   * @param operation the operation to perform, e.g. crs-compound, crs, axis, ...
   */
  public ResolveRequest(String operation) {
    this(operation, Config.getInstance().getServiceUrl(), null);
  }
  
  /**
   * Create a new request to resolve.
   * 
   * @param operation the operation to perform, e.g. crs-compound, crs, axis, ...
   * @param service the service's URL, e.g. http://www.opengis.net/def
   */
  public ResolveRequest(String operation, String service, String fullUri) {
    params = new ArrayList<Pair<String, String>>();
    this.operation = operation;
    this.service = service;
    this.fulUri = fullUri;
    this.expand = EXPAND_DEFAULT;
  }

  /**
   * Add a new parameter to the request.
   * 
   * @param key must not be null
   * @param value the value, can be null
   */
  public void addParam(String key, String value) throws SecoreException {
    if (key == null) {
      throw new SecoreException(ExceptionCode.InvalidRequest, "Null key encountered");
    }
    if (key.equalsIgnoreCase(EXPAND_KEY) && value != null) {
      if (value.equalsIgnoreCase(EXPAND_NONE)) {
        expand = 0;
      } else if (value.equalsIgnoreCase(EXPAND_FULL)) {
        expand = Integer.MAX_VALUE;
      } else {
        try {
          expand = Integer.parseInt(value);
          if (expand < 0) {
            throw new Exception();
          }
        } catch (Exception ex) {
          throw new SecoreException(ExceptionCode.InvalidRequest, "Invalid expand level specified: " + value);
        }
      }
    } else {
      params.add(Pair.of(key, value));
    }
  }

  /**
   * @return the parameters this request holds
   */
  public List<Pair<String, String>> getParams() {
    return params;
  }
  
  public String paramsToString() {
    String ret = EMPTY;
    for (Pair<String, String> p : params) {
      if (p.snd == null || p.snd.equals(EMPTY)) {
        ret += REST_SEPARATOR + p.fst;
      } else {
        ret += PAIR_SEPARATOR + p.fst + KEY_VALUE_SEPARATOR + p.snd;
      }
    }
    if (expand != EXPAND_DEFAULT) {
      ret += PAIR_SEPARATOR + EXPAND_KEY + KEY_VALUE_SEPARATOR + expand;
    }
    return ret;
  }
  
  /**
   * @return the operation of this request
   */
  public String getOperation() {
    return operation;
  }
  
  /**
   * @return the service URL
   */
  public String getService() {
    return service;
  }
  
  /**
   * @return the full URL for this request
   */
  public String getFullUri() {
    return fulUri;
  }
  
  /**
   * @return the depth to which links in the returned XML document should be resolved
   */
  public int getExpandDepth() {
    return expand;
  }

  @Override
  public String toString() {
    return "ResolveRequest {\n" +
        "\tparams=" + params +
        "\n\toperation=" + operation +
        "\n\tservice=" + service +
        "\n\tfullUri=" + fulUri +
        "\n\texpand=" + expand +
        "\n}";
  }
}
