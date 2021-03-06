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
/*************************************************************
 *
 *
 * PURPOSE:
 *
 *
 * COMMENTS:
 *
 ************************************************************/

#include "config.h"
#include "raslib/rmdebug.hh"

#include "qlparser/qtconcat.hh"
#include "qlparser/qtdata.hh"
#include "qlparser/qtmdd.hh"
#include "tilemgr/tile.hh"
#include "catalogmgr/typefactory.hh"

#include "mddmgr/mddobj.hh"

#include <iostream>
#ifndef CPPSTDLIB
#include <ospace/string.h> // STL<ToolKit>
#else
#include <string>
using namespace std;
#endif


const QtNode::QtNodeType QtConcat::nodeType = QT_CONCAT;

QtConcat::QtConcat( QtOperationList* opList, unsigned int dim )
    :  QtNaryOperation( opList ),
       dimension( dim )
{
}

bool
QtConcat::equalMeaning( QtNode* node )
{
    RMDBCLASS( "QtConcat", "equalMeaning( QtNode* )", "qlparser", __FILE__, __LINE__ )

    bool result = false;

    if( nodeType == node->getNodeType() )
    {
        QtConcat* condNode;
        condNode = (QtConcat*) node; // by force

        // check domain and cell expression
        result  = QtNaryOperation::equalMeaning( condNode );

        // check dimension
        result &= ( this->dimension == condNode->getDimension() );
    };

    return ( result );
}



string
QtConcat::getSpelling()
{

    char tempStr[20];
    sprintf(tempStr, "%lu", (unsigned long)getNodeType());
    string result  = string(tempStr);
    result.append( "(" );
    result.append( QtNaryOperation::getSpelling() );
    result.append( "," );

    sprintf(tempStr, "%u", dimension);
    result.append( string(tempStr) );

    result.append( ")" );

    return result;
}

void
QtConcat::simplify()
{
    RMDBCLASS( "QtConcat", "simplify()", "qlparser", __FILE__, __LINE__ )

    RMDBGMIDDLE(1, RMDebug::module_qlparser, "QtConcat", "simplify() warning: QtConcat itself is not simplified yet")

    // Default method for all classes that have no implementation.
    // Method is used bottom up.

    QtNodeList* resultList=NULL;
    QtNodeList::iterator iter;

    resultList = getChilds( QT_DIRECT_CHILDS );
    for( iter=resultList->begin(); iter!=resultList->end(); iter++ )
        (*iter)->simplify();

    delete resultList;
    resultList=NULL;
}


QtData*
QtConcat::evaluate( QtDataList* inputList )
{

    RMDBCLASS( "QtConcat", "evaluate( QtDataList* )", "qlparser", __FILE__, __LINE__ )

    QtData* returnValue = NULL;
    QtDataList* operandList = NULL;
    
    QtDataList::iterator iter;

    if( getOperands( inputList, operandList ) )
    {

#ifdef QT_RUNTIME_TYPE_CHECK

    for( iter=operandList->begin(); iter!=operandList->end(); iter++ )
        if( (*iter)->getDataType() != QT_MDD)
        {
            RMInit::logOut << "Internal error in QtConcat::evaluate() - "
                         << "runtime type checking failed (MDD)." << endl;

            // delete old operand list
            if( operandList ) operandList->deleteRef();

            return 0;
        }
#endif

        // check if type coercion is possible and compute the result type       
        const BaseType* baseType;

        for( iter=operandList->begin(); iter!=operandList->end(); iter++ ) {
            if (iter == operandList->begin()) {
                QtMDD* qtMDDObj = (QtMDD*)(*iter);
                MDDObj* currentMDDObj = qtMDDObj->getMDDObject();
                baseType = (currentMDDObj->getMDDBaseType())->getBaseType();
            } else {
                QtMDD* qtMDDObj2 = (QtMDD*)(*iter);
                MDDObj* currentMDDObj2 = qtMDDObj2->getMDDObject(); 
                const BaseType* baseType2 = (currentMDDObj2->getMDDBaseType())->getBaseType();
                baseType = getResultType( baseType, baseType2 );           
                if(!baseType)
                {
                    RMInit::logOut << "Error: QtConcat::evaluate( QtDataList* ) - operand types are incompatible" << endl;
                    parseInfo.setErrorNo(352);
                    throw parseInfo;

                    if( operandList ) {
                        delete operandList;
                        operandList = NULL;
                    }

                    return 0;
                }
            }
        }
        
        MDDBaseType* resultMDDType = new MDDBaseType( "tmptype", baseType );
        TypeFactory::addTempType( resultMDDType );
        
        // compute the result domain
        r_Point tVector[operandList->size()]; // save the translating vectors for all arrays except the first
        r_Minterval destinationDomain;
        int i = 0;
        for( iter=operandList->begin(); iter!=operandList->end(); iter++, i++ ) {
            
            if (iter == operandList->begin()) {
                QtMDD* qtMDDObj = (QtMDD*)(*iter);
                destinationDomain = qtMDDObj->getLoadDomain();
                if (destinationDomain.dimension() <= (r_Dimension) dimension) {
                    if( operandList ) {
                        delete operandList;
                        operandList = NULL;
                    }

                    RMInit::logOut << "Error: QtConcat::evaluate( QtDataList* ) - the operands have less dimensions than the one specified" << endl;
                    parseInfo.setErrorNo(424);
                    throw parseInfo;                    
                }
            } else {
                QtMDD* qtMDDObj2 = (QtMDD*)(*iter);
                
                r_Minterval domB = qtMDDObj2->getLoadDomain();
                r_Point* difA = new r_Point(destinationDomain.dimension());
                (*difA)[dimension] = destinationDomain.get_extent()[dimension];
                const r_Point& newPosB = destinationDomain.get_origin() + *difA; // compute target position of the array in the result
                delete difA;
                r_Point transVector = newPosB - domB.get_origin(); // translating vector as a difference between intial lower left corner and target position in the new array
                tVector[i] = transVector;
                
                const r_Minterval& dummyB = domB.create_translation(transVector); 
                if (destinationDomain.is_mergeable(dummyB)) 
                {                
                    destinationDomain = dummyB.create_closure(destinationDomain);        
                } 
                else
                {
                    if( operandList ) {
                        delete operandList;
                        operandList = NULL;
                    }

                    RMInit::logOut << "Error: QtConcat::evaluate( QtDataList* ) - r_Mintervals of operands not mergeable" << endl;
                    parseInfo.setErrorNo(425);
                    throw parseInfo;
                }
            }
        }
        
        // create a transient MDD object for the query result 
        MDDObj* resultMDD = new MDDObj( resultMDDType, destinationDomain );
 
        i = 0;
        for( iter=operandList->begin(); iter!=operandList->end(); iter++, i++ ) {
            
            if (iter == operandList->begin()) { // only for the first array, which shouldn't be shifted
                QtMDD* qtMDDObj = (QtMDD*)(*iter);
                MDDObj* currentMDDObj = qtMDDObj->getMDDObject();
                // get all tiles
                vector<Tile* >* tilesA = currentMDDObj->intersect( qtMDDObj->getLoadDomain() );

                // iterate over source tiles
                for( vector<Tile*>::iterator tileIter = tilesA->begin(); tileIter != tilesA->end(); tileIter++ )
                {
                  // get relevant area of source tile
                  r_Minterval tileDomain = qtMDDObj->getLoadDomain().create_intersection( (*tileIter)->getDomain() );

                  // create a new transient tile, copy the transient data, and insert it into the mdd object
                  Tile* newTransTile = new Tile( tileDomain, baseType );
                  UnaryOp* myOp = NULL;
                  myOp = Ops::getUnaryOp(Ops::OP_CAST_DOUBLE, baseType, currentMDDObj->getCellType(), 0, 0); // OP_CAST_DOUBLE is used just for identifying the operation as cast 
                  newTransTile->execUnaryOp(myOp, tileDomain, *tileIter, tileDomain);
                  resultMDD->insertTile( newTransTile );
                }
                
                // delete the tile vectors, the tiles themselves are deleted when the destructor
                // of the MDD object is called
                delete tilesA;
                tilesA=NULL;

            } else { // all other arrays, which are shifted
                QtMDD* qtMDDObj2 = (QtMDD*)(*iter);
                MDDObj* currentMDDObj2 = qtMDDObj2->getMDDObject();
                // get all tiles
                vector<Tile* >* tilesB = currentMDDObj2->intersect( qtMDDObj2->getLoadDomain() );

                // iterate over source tiles
                for( vector<Tile*>::iterator tileIter = tilesB->begin(); tileIter != tilesB->end(); tileIter++ )
                {
                  // get relevant area of source tile
                  r_Minterval sourceTileDomain = qtMDDObj2->getLoadDomain().create_intersection( (*tileIter)->getDomain() );

                  // compute translated tile domain
                  r_Minterval destinationTileDomain = sourceTileDomain.create_translation( tVector[i] );

                  // create a new transient tile, copy the transient data, and insert it into the mdd object
                  Tile* newTransTile = new Tile( destinationTileDomain, baseType );
                  UnaryOp* myOp = NULL;
                  myOp = Ops::getUnaryOp(Ops::OP_CAST_DOUBLE, baseType, currentMDDObj2->getCellType(), 0, 0); // OP_CAST_DOUBLE is used just for identifying the operation as cast 
                  newTransTile->execUnaryOp(myOp, destinationTileDomain, *tileIter, sourceTileDomain);
                  resultMDD->insertTile( newTransTile );
                }

                // create a new QtMDD object as carrier object for the transient MDD object
                returnValue = new QtMDD( (MDDObj*)resultMDD );

                // delete the tile vectors, the tiles themselves are deleted when the destructor
                // of the MDD object is called
                delete tilesB;
                tilesB=NULL;                

            }
        }

        // delete old operands
        if( operandList ) {
            delete operandList;
            operandList = NULL;
        }
        
    }
    
    return returnValue;
}



void
QtConcat::printTree( int tab, ostream& s, QtChildType mode )
{
    s << SPACE_STR(tab).c_str() << "QtConcat Object " << getNodeType() << endl;

    QtNaryOperation::printTree( tab, s, mode );
}



void
QtConcat::printAlgebraicExpression( ostream& s )
{
    s << "concat(";

    if( operationList )
    {
        QtOperationList::iterator iter;

        for( iter=operationList->begin(); iter!=operationList->end(); iter++ )
        {
            if( iter!=operationList->begin() ) s << ",";

            if( *iter )
                (*iter)->printAlgebraicExpression( s );
            else
                s << "<nn>";
        }
    }
    else
        s << "<nn>";
        
    s << "; " << dimension;
    
    s << ")";
}



const QtTypeElement&
QtConcat::checkType( QtTypeTuple* typeTuple )
{
    RMDBCLASS( "QtConcat", "checkType( QtTypeTuple* )", "qlparser", __FILE__, __LINE__ )

    dataStreamType.setDataType( QT_TYPE_UNKNOWN );

    QtTypeElement inputType;
    // check operand branches
    if( operationList )
    {
        QtOperationList::iterator iter;

        for( iter=operationList->begin(); iter!=operationList->end(); iter++ )
        {

            if( *iter )
                inputType = (*iter)->checkType( typeTuple );
            else
                RMInit::logOut << "Error: QtConcat::checkType() - operand branch invalid." << endl;
            
            if( inputType.getDataType() != QT_MDD )
            {
                RMInit::logOut << "Error: QtConcat::checkType() - every operand must be of type MDD." << endl;
                parseInfo.setErrorNo(423);
                throw parseInfo;
            }     
                
        }
        
        dataStreamType.setDataType( QT_MDD );
    }
    else
        RMInit::logOut << "Error: QtConcat::checkType() - operand branch invalid." << endl;    
    
    return dataStreamType;
}


QtNode::QtAreaType
QtConcat::getAreaType()
{
    return QT_AREA_MDD;
}

const BaseType* QtConcat::getResultType(const BaseType* op1, const BaseType* op2)
{

    if ((op1->getType() == STRUCT) || (op2->getType() == STRUCT))
    {
        if (op1->compatibleWith(op2))
        {
            return op1;
        }
        else
        {
            return NULL;
        }
    }
    if (op1->getType() == op2->getType())
    {
        return op1;
    }


    // if only one of operand is signed, result also has to be signed.
    if( isSignedType(op1) && !isSignedType(op2) )
    {
        // swap it, action is in next if clause
        const BaseType* dummy;
        dummy = op2;
        op2 = op1;
        op1 = dummy;
    }
    if( !isSignedType(op1) && isSignedType(op2) )
    {
        // got to get the thing with the highest precision and make sure
        // it is signed.
        if( op2->getType() == COMPLEXTYPE1 || op2->getType() == COMPLEXTYPE2 ||
                op2->getType() == FLOAT || op2->getType() == DOUBLE || op2->getType() == LONG )
            return (BaseType*)op2;
        if( op1->getType() == USHORT )
            return TypeFactory::mapType("Short");
        if( op2->getType() == SHORT )
            return (BaseType*)op2;
        return TypeFactory::mapType("Octet");
    }
    // return the stronger type
    if(op1->getType() == COMPLEXTYPE2 || op2->getType() == COMPLEXTYPE2)
        return TypeFactory::mapType("Complex2");
    if(op1->getType() == COMPLEXTYPE1 || op2->getType() == COMPLEXTYPE1)
        return TypeFactory::mapType("Complex1");
    if(op1->getType() == DOUBLE || op2->getType() == DOUBLE)
        return TypeFactory::mapType("Double");
    if(op1->getType() == FLOAT || op2->getType() == FLOAT)
        return TypeFactory::mapType("Float");
    if(op1->getType() <= op2->getType())
        return (BaseType*)op1;
    else
        return (BaseType*)op2;
        
    return NULL;
}


int QtConcat::isSignedType( const BaseType* type )
{
    return ( type->getType() >= LONG && type->getType() <= COMPLEXTYPE2 );
}
