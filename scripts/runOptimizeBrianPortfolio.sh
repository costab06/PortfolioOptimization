BASEDIR=/home/bcosta/Java


CLASSPATH=\
$BASEDIR/cots/jini/jini2_1/lib/jini-core.jar:\
$BASEDIR/cots/jini/jini2_1/lib/jini-ext.jar:\
$BASEDIR/cots/mysql/mysql-connector-java-5.0.4/mysql-connector-java-5.0.4-bin.jar:\
$BASEDIR/cots/jfreechart/jfreechart-1.0.1/lib/jfreechart-1.0.1.jar:\
$BASEDIR/cots/jfreechart/jfreechart-1.0.1/lib/jcommon-1.0.0.jar:\
$BASEDIR/cots/computefarm/computefarm-0.8.2/computefarm-0.8.2.jar:\
$BASEDIR/dist/lib/BCFinancial.jar


java -cp $CLASSPATH -Djava.security.policy=src/policy/policy.all com.bcfinancial.portfolio.OptimizePortfolio 2006 10 1 2 90 brian fidelity

