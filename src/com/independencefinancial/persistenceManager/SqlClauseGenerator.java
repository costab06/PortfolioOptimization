package com.bcfinancial.persistenceManager;

import java.util.Vector;


public class SqlClauseGenerator {

    private int numColumns;
    private String keyFieldName = null;
    private String columnNameList = null;
    
    public SqlClauseGenerator(Class aClass, TypeField keyTypeField, Vector ordinaryTypeFields) {
	numColumns = 1 + ordinaryTypeFields.size();
	keyFieldName = keyTypeField.getName();
	columnNameList = keyTypeField.getName()+", ";

	for (int i=0; i<ordinaryTypeFields.size(); i++) {
	    Object o = ordinaryTypeFields.elementAt(i);
	    if (o instanceof TypeField)
		this.columnNameList += ((TypeField) o).getName();
	    else if (o instanceof ArrayField)
		this.columnNameList += ((ArrayField) o).getName();
	    else 
		this.columnNameList += ((SerializableField) o).getName();
	    if (i < ordinaryTypeFields.size()-1)
		this.columnNameList += ", ";
	}
	
    }
    
    public int numColumns() {
	return numColumns;
    }
    
    public String columnNameList() {
	return columnNameList;
    }
    
    public String keyName() {
	return keyFieldName;
    }
    
}
