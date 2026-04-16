BASEDIR=/home/bcosta/Java

CLASSPATH=$BASEDIR/cots/mysql/mysql-connector-java-5.0.4/mysql-connector-java-5.0.4-bin.jar:$BASEDIR/cots/jfreechart/jfreechart-1.0.1/lib/jfreechart-1.0.1.jar:$BASEDIR/cots/jfreechart/jfreechart-1.0.1/lib/jcommon-1.0.0.jar:$BASEDIR/cots/poi/poi-2.5.1/poi-2.5.1-final-20040804.jar:$BASEDIR/dist/lib/BCFinancial.jar

java -cp $CLASSPATH com.bcfinancial.user.UserAddPortfolio brian fidelity /home/bcosta/portfolios/brian_fidelity.xls

