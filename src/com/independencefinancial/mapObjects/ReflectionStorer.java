package com.bcfinancial.mapObjects;


import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public class ReflectionStorer extends GeneralStorer {
  public ReflectionStorer (ObjectStorage storage) {
    super (storage);
  }

  protected StorageFields getFields (Object object) {
    Class clazz = object.getClass ();
    String className = clazz.getName ();
    StorageFields fields = new StorageFields (className);
    try {
      Map suffixes = new HashMap ();
      do {
        getFields (fields, object, clazz, suffixes);
        clazz = clazz.getSuperclass ();
      } while (clazz != null);
    } catch (IllegalAccessException ignored) {
    }
    return fields;
  }

  private void getFields (StorageFields fields, Object object, Class clazz, Map suffixes) throws IllegalAccessException {
    Field[] classFields = clazz.getDeclaredFields ();
    AccessibleObject.setAccessible (classFields, true);
    int n = classFields.length;
    for (int i = 0; i < n; ++ i) {
      Field field = classFields[i];
      if (isValid (field)) {
        String name = field.getName ();
        Class type = field.getType ();
        Object value = field.get (object);
        StringBuffer suffix = (StringBuffer) suffixes.get (name);
        if (suffix == null)
          suffixes.put (name, suffix = new StringBuffer ());
        fields.addField (name + suffix, type, value);
        suffix.append ('\'');
      }
    }
  }
  
  protected Object setFields (RetrievalFields fields) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    String className = fields.getClassName ();
    Class clazz = Class.forName (className);
    Object object = clazz.newInstance ();
    Map suffixes = new HashMap ();
    do {
      setFields (object, fields, clazz, suffixes);
      clazz = clazz.getSuperclass ();
    } while (clazz != null);
    return object;
  }

  private void setFields (Object object, RetrievalFields fields, Class clazz, Map suffixes) throws IllegalAccessException {
    Field[] classFields = clazz.getDeclaredFields ();
    AccessibleObject.setAccessible (classFields, true);
    int n = classFields.length;
    for (int i = 0; i < n; ++ i) {
      Field field = classFields[i];
      if (isValid (field)) {
        String name = field.getName ();
        Class type = field.getType ();

	System.out.println(" About to set field: "+name+" of type: "+type);

        StringBuffer suffix = (StringBuffer) suffixes.get (name);
	
	System.out.println(" Suffix: "+suffix);

        if (suffix == null)
          suffixes.put (name, suffix = new StringBuffer ());
        Object value = fields.getValue (name + suffix, type);

	System.out.println(" Object in the retrieval fields is: "+value);	
        field.set (object, value);


        suffix.append ('\'');
      }
    }
  }

  private boolean isValid (Field field) {
    int modifiers = field.getModifiers ();
    return (!Modifier.isTransient (modifiers) &&
            !Modifier.isStatic (modifiers) &&
            !Modifier.isFinal (modifiers));
  }
}
