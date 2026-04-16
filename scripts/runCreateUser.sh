BASEDIR=/home/bcosta/Java

CLASSPATH=$BASEDIR/cots/mysql/mysql-connector-java-5.0.4/mysql-connector-java-5.0.4-bin.jar:$BASEDIR/cots/jfreechart/jfreechart-1.0.1/lib/jfreechart-1.0.1.jar:$BASEDIR/cots/jfreechart/jfreechart-1.0.1/lib/jcommon-1.0.0.jar:$BASEDIR/dist/lib/BCFinancial.jar

java -cp $CLASSPATH com.bcfinancial.user.CreateUser $1 $2 $3 $4 $5


