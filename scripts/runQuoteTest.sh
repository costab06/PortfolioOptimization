BASEDIR=/home/bcosta/Java

CLASSPATH=\
$BASEDIR/cots/axis/axis-1_4/lib/axis.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/saaj.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/commons-logging.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/commons-discovery.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/wsdl4j.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/jaxrpc.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/activation.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/mail.jar:\
$BASEDIR/cots/axis/axis-1_4/lib/xmlsec.jar:\
$BASEDIR/dist/lib/BCFinancial.jar

echo $CLASSPATH

java -cp $CLASSPATH com.bcfinancial.webservices.QuoteTest


