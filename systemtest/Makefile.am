# -*-Makefile-*-
#
# This file is part of rasdaman community.
#
# Rasdaman community is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Rasdaman community is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with rasdaman community.  If not, see <http://www.gnu.org/licenses/>.
#
# Copyright 2003, 2004, 2005, 2006, 2007, 2008, 2009 Peter Baumann /
# rasdaman GmbH.
#
# For more information please see <http://www.rasdaman.org>
# or contact Peter Baumann via <baumann@rasdaman.com>.
# 
# MAKEFILE FOR:  
#   perform systemtest (overall rasdaman test suite)
# 
# COMMENTS: 
# - not yet operational
#
##################################################################
######################### Definitions ############################

# all test programs


########################### Targets ##############################
TESTSEQUENCE = $(shell) ls -d testcases_mandatory/* \
                             testcases_open/* \
                             testcases_fixed/*
# general system test
.PHONY: check
check:
	ret=0; \
	for testcase in ${TESTSEQUENCE}; do\
		 RMANBASE=${RMANBASE}\
		 PATH=${bindir}:${PATH}\
		 PROGTEST=$${testcase}/*.sh;\
     if [ ! -f $$testcase/*.sh ]; then\
       continue;\
     fi;\
     $$testcase/*.sh;\
     tmp=$$?;\
		 if [ $$tmp -ne 0 ]; \
		 then\
      ret=$$tmp;\
			echo $${PROGTEST} ... FAIL>>log;\
		 else \
		 	echo $${PROGTEST} ... OK>>log;\
		 fi; \
		 echo "	"see detail in $$testcase/log>>log;\
	done;\
	cat log;\
	rm log;\
  exit $$ret;
	
clean:
	for testcase in ${TESTSEQUENCE}; do \
               PATH=${bindir}:${PATH}   rm -rf $${testcase}/log* $${testcase}/failed_cases; \
        done;

######################## Dependencies ############################

