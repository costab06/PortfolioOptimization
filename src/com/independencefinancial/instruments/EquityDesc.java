package com.bcfinancial.instruments;

import com.bcfinancial.persistenceManager.Persistable;

public class EquityDesc extends Persistable {
    private String symbol;
    private String description;
    
    public EquityDesc() {} // need a default constructor
    
    public EquityDesc(String symbol, String description) {
	this.symbol = symbol;
	this.description = description;
    }

    public static void init(String tableName) {
	// ignore
    }


    public static String getKeyFieldName() {
	return "symbol";
    }

    
    public static String getCreateTableString(String tableName) {
	String createTableSql="create table "+tableName+" (symbol varchar(10)not null primary key , description varchar(80) not null)";
	return createTableSql;
    }
    
    public String toString() {
	return symbol+" "+description;
    }
}    
