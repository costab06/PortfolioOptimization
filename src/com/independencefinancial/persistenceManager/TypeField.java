package com.bcfinancial.persistenceManager;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class TypeField {
    private static boolean DEBUG=false;
    private Field field;
    
    public TypeField(Field field) {
	this.field = field;
    }
    
    public static TypeField makeTypeField(Field field) throws CannotPersistException {
	if (!persistablePrimitive(field)) {
	    String s = "field: "+field+" should not be persisted because it is final, static, or transient - return null";
	    return null;
	}
	Class fieldType = field.getType();
	if (fieldType == java.lang.String.class ||
	    fieldType == java.util.Date.class ||
	    fieldType == java.lang.Integer.class ||
	    fieldType == java.lang.Integer.TYPE ||
	    fieldType == java.lang.Long.class ||
	    fieldType == java.lang.Long.TYPE ||
	    fieldType == java.lang.Byte.class ||
	    fieldType == java.lang.Byte.TYPE ||
	    fieldType == java.lang.Character.class ||
	    fieldType == java.lang.Character.TYPE ||
	    fieldType == java.lang.Float.class ||
	    fieldType == java.lang.Float.TYPE ||
	    fieldType == java.lang.Double.class ||
	    fieldType == java.lang.Double.TYPE ||
	    fieldType == java.lang.Boolean.class ||
	    fieldType == java.lang.Boolean.TYPE ||
	    fieldType == java.sql.Timestamp.class ) {
	    return new TypeField(field);
	}
	String s = "TypeField: fieldType not of supported type: "+fieldType;
	debug(s);
	throw new CannotPersistException(s);
    }
    
    private static boolean persistablePrimitive(Field field) {
	int modifier = field.getModifiers();
	return ! (Modifier.isFinal(modifier)) &&
	    ! (Modifier.isStatic(modifier)) &&
	    ! (Modifier.isTransient(modifier));
    }

    public String getName() {
	return field.getName();
    }
    
    public Object get(Object o) {
	Object ret = null;
	try {
	    field.setAccessible(true);
	    ret = field.get(o);
	    field.setAccessible(false);
	} catch (Exception e) {
	    System.err.println("Exception accessing field: "+e);
	}
	return ret;
	
    }
    
    
    public void set (Object o, Object value) {
	try {
	    field.setAccessible(true);
	    field.set(o,value);
	    field.setAccessible(false);
	} catch (Exception e) {
	    System.err.println("Exception accessing field: "+e);
	}
    }
    
    private static void debug(String s) {
	if(DEBUG)
	    System.out.println(s);
    }
    
}
    
