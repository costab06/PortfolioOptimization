package com.bcfinancial.persistenceManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.io.Serializable;

public class SerializableField {
    private static boolean DEBUG=false;
    private Field field;
    
    public SerializableField(Field field) {
	this.field = field;
    }

    public static SerializableField makeSerializableField(Field field) throws CannotPersistException {
	if (!persistablePrimitive(field)) {
	    String s = "field: "+field+" should not be persisted because it is final, static, or transient - return null";
	    debug(s);
	    return null;
	}
	
	Class fieldType = field.getType();
	if (fieldType instanceof Serializable) {
	    return new SerializableField(field);
	}
	String s = "SerializableField: fieldType not of supported type: "+fieldType;
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
	if (DEBUG)
	    System.out.println(s);
    }
    
}

