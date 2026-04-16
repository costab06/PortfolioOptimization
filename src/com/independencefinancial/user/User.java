package com.bcfinancial.user;

import java.util.Date;
import java.io.Serializable;
import com.bcfinancial.data.converter.StringArrayConverter;
import com.bcfinancial.data.converter.UtilityFunctionConverter;
import com.bcfinancial.persistenceManager.Persistable;
import com.bcfinancial.utility.*;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
@Access(AccessType.FIELD)
public class User extends Persistable {

    @Id
    @Column(name = "username", length = 255, nullable = false)
    private String username;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "emailAddress", length = 255, nullable = false)
    private String emailAddress;

    @Convert(converter = StringArrayConverter.class)
    @Column(name = "portfolioNames", length = 8192)
    private String[] portfolioNames;

    @Convert(converter = UtilityFunctionConverter.class)
    @Column(name = "utilityFunction", columnDefinition = "BLOB")
    private UtilityFunction utilityFunction;

    @Column(name = "timeStamp")
    private double timeStamp;

    public User() {} // required by JPA

    public User (String username, String password) {
	this.username = username;
	this.password = password;
	this.emailAddress = new String();
	this.portfolioNames = new String[0];
	this.utilityFunction = getDefaultUtilityFunction(0.88, 0.88, 2.25);
	this.timeStamp = (double)(new Date()).getTime();
    }

    public User (String username, String password, String emailAddress, String[] portfolioNames, double alpha, double beta, double lambda) {
	this.username = username;
	this.password = password;
	this.emailAddress = emailAddress;
	this.portfolioNames = portfolioNames;
	this.utilityFunction = getDefaultUtilityFunction(alpha, beta, lambda);
	this.timeStamp = (double)(new Date()).getTime();
    }

    public static String getKeyFieldName() {
	return "username";
    }

    public static void init(String tableName) {
    }

    public void updateTimeStamp() {
	timeStamp = (double)(new Date()).getTime();
    }

    public Date getTimeStamp() {
	return new Date((long)timeStamp);
    }

    public String getUsername() {
	return username;
    }

    public String getPassword() {
	return password;
    }

    public void setEmailAddress(String emailAddress) {
	this.emailAddress = emailAddress;
    }

    public String getEmailAddress() {
	return emailAddress;
    }

    public void setPortfolioNames(String[] names) {
	this.portfolioNames = names;
    }

    public String[] getPortfolioNames() {
	return portfolioNames;
    }

    public void addPortfolioName(String portfolioName) {
	String[] temp = new String[portfolioNames.length + 1];
	for (int i=0;i<portfolioNames.length;i++)
	    temp[i] = portfolioNames[i];
	temp[temp.length-1] = portfolioName;
	portfolioNames = temp;
    }

    public void setUtilityFunction(UtilityFunction utilityFunction) {
	this.utilityFunction = utilityFunction;
    }

    public UtilityFunction getUtilityFunction() {
	return utilityFunction;
    }

    public static String getCreateTableString(String tableName) {
	String createTableSql="create table "+tableName+" (username varchar(12) primary key not null, password varchar(12) not null, emailAddress varchar(80) not null, portfolioNames varbinary(10240), utilityFunction varbinary(10240) not null, timeStamp double not null);";
	return createTableSql;
    }

    public String toString() {
	String ret = "| "+username+" | "+password+" | "+emailAddress+" | ";
	if (portfolioNames != null) {
	    for (int i=0;i<portfolioNames.length;i++) {
		ret += portfolioNames[i]+"\n";
	    }
	}
	ret += (new Date((long)timeStamp))+" | ";
	return ret;
    }

    private UtilityFunction getDefaultUtilityFunction(double alpha, double beta, double lambda) {
	return new ProspectTheoryUtilityFunction(alpha, beta, lambda);
    }
}
