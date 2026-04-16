JAVA_HOME=/usr/java/jdk1.5.0_07/jre
JINI_HOME=/usr/jini/jini2_1/lib/
START_CONFIG=src/com/bcfinancial/persistenceService/config/start-persistence.config

$JAVA_HOME/bin/java -Djava.security.policy=policy/policy.all -jar $JINI_HOME/start.jar $START_CONFIG
