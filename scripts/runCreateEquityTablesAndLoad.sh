CLASSPATH=cots/mysql/mysql-connector-java-5.0.4/mysql-connector-java-5.0.4-bin.jar:cots/jfreechart/jfreechart-1.0.1/lib/jfreechart-1.0.1.jar:cots/jfreechart/jfreechart-1.0.1/lib/jcommon-1.0.0.jar:dist/lib/BCFinancial.jar

java -cp $CLASSPATH -Xms32m -Xmx128m com.bcfinancial.loadData.CreateEquityTablesAndLoad
