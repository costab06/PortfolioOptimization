
BASEDIR=/usr/local

CLASSPATH=$BASEDIR/jini/lib/jsk-lib.jar:$BASEDIR/blitz/lib/blitz.jar:$BASEDIR/jini/lib/jsk-platform.jar:$BASEDIR/jini/lib/sun-util.jar

java -classpath $CLASSPATH -Djava.security.policy=src/policy/policy.all org.dancres.blitz.tools.SyncAndShutdown Blitz_JavaSpace
