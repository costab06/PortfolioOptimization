package com.bcfinancial.mapObjects;

import java.io.*;
import java.util.*;

public class SerializationStorer extends GeneralStorer implements ObjectStreamConstants {
  private Storer storer;
  private Retriever retriever;
  
  public SerializationStorer (ObjectStorage storage) {
    super (storage);
    storer = new Storer ();
    retriever = new Retriever ();
  }

  protected StorageFields getFields (Object object) throws IOException {
    byte[] data = storer.encode (object);
    StorageFields fields = storer.decode (data);
    return fields;
  }

  protected Object setFields (RetrievalFields fields) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
    byte[] data = retriever.encode (fields);
    Object object = retriever.decode (data);
    return object;
  }
  
  void asserttest (boolean assertion, String description) throws IOException {
    if (!assertion)
      throw new IOException ("Assertion failed: " + description);
  }

  static String stringSig = "Ljava/lang/String;";

  static Map typeCodeMap = new HashMap ();
  static {
    typeCodeMap.put (new Character ('Z'), boolean.class);
    typeCodeMap.put (new Character ('B'), byte.class);
    typeCodeMap.put (new Character ('S'), short.class);
    typeCodeMap.put (new Character ('I'), int.class);
    typeCodeMap.put (new Character ('J'), long.class);
    typeCodeMap.put (new Character ('F'), float.class);
    typeCodeMap.put (new Character ('D'), double.class);
    typeCodeMap.put (new Character ('C'), char.class);
  }

  private static Class typeOf (char tc) {
    return (Class) typeCodeMap.get (new Character (tc));
  }
  
  class Storer {
    private byte[] encode (Object object) throws IOException {
      ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream ();
      ObjectOutputStream objectOut = new ObjectOutputStream (byteArrayOut);
      objectOut.writeObject (object);
      objectOut.close ();
      byte[] encoded = byteArrayOut.toByteArray ();
      return encoded;
    }

    private int wireOffset = 0;
    private Map wireMap = new HashMap ();

    private void wireReset () {
      wireOffset = 0;
      wireMap.clear ();
    }

    private void wireOffset (Object o) {
      int i = wireOffset ++;
      if (o != null) {
        Integer ii = new Integer (i);
        wireMap.put (ii, o);
      }
    }

    private Object wireGet (int i) {
      Integer ii = new Integer (i);
      Object o = wireMap.get (ii);
      return o;
    }

    private StorageFields decode (byte[] data) throws IOException {
      ByteArrayInputStream byteArrayIn = new ByteArrayInputStream (data);
      DataInputStream dataIn = new DataInputStream (byteArrayIn);
      asserttest (dataIn.readShort () == STREAM_MAGIC, "magic");
      asserttest (dataIn.readShort () == STREAM_VERSION, "version");
      wireReset ();
      StorageFields fields = decodeObject (dataIn);
      return fields;
    }
  
    private StorageFields decodeObject (DataInputStream data) throws IOException {
      asserttest (data.readByte () == TC_OBJECT, "object");
      List fields = new ArrayList (), fields_;
      Map suffixes = new HashMap ();
      String className = null, className_;
      while ((className_ = decodeClassDescriptor (fields, suffixes, data)) != null) {
        if (className == null)
          className = className_;
      }
      wireOffset (null);
      asserttest (className != null, "class name");
      StorageFields objectFields = new StorageFields (className);
      int numClasses = fields.size ();
      for (int i = numClasses - 1; i >= 0; -- i) {
        fields_ = (List) fields.get (i);
        decodeObjectFields (objectFields, data, fields_, suffixes);
      }
      return objectFields;
    }

    private String decodeClassDescriptor (List fields, Map suffixes, DataInputStream data) throws IOException {
      byte tc = data.readByte ();
      if (tc == TC_NULL)
        return null;
      asserttest (tc == TC_CLASSDESC, "class descriptor");
      wireOffset (null);
      String name = data.readUTF ();
      long uid = data.readLong ();
      byte flags = data.readByte ();
      asserttest ((flags & SC_SERIALIZABLE) != 0, "serializable");
      asserttest ((flags & SC_EXTERNALIZABLE) == 0, "!externalizable");
      asserttest ((flags & SC_WRITE_METHOD) == 0, "!writeObject");
      int numFields = data.readShort ();
      List list = new ArrayList (numFields);
      for (int i = 0; i < numFields; ++ i) {
        decodeClassField (list, suffixes, data);
      }
      asserttest (data.readByte () == TC_ENDBLOCKDATA, "end block data");
      fields.add (list);
      return name;
    }
        
    private void decodeClassField (List list, Map suffixes, DataInputStream data) throws IOException {
      char tc = (char) data.readByte (); // watch the negatives
      asserttest (tc != '[', "!array");
      String name = data.readUTF ();
      Class type;
      if (tc == 'L') {
        String sig = readString (data);
        asserttest (stringSig.equals (sig), "!reference");
        type = String.class;
      } else {
        type = typeOf (tc);
        asserttest (type != null, "known type");
      }
      list.add (name);
      list.add (type);
      if (suffixes.containsKey (name))
        ((StringBuffer) suffixes.get (name)).append ('\'');
      else
        suffixes.put (name, new StringBuffer ());
    }

    private void decodeObjectFields (StorageFields objectFields, DataInputStream data, List fields, Map suffixes) throws IOException {
      Iterator iter = fields.iterator ();
      while (iter.hasNext ()) {
        String name = (String) iter.next ();
        Class type = (Class) iter.next ();
        Object value = decodeObjectField (data, type);
        StringBuffer suffix = (StringBuffer) suffixes.get (name);
        objectFields.addField (name + suffix, type, value);
        int n = suffix.length ();
        if (n > 0)
          suffix.setLength (n - 1);
      }
    }

    private Object decodeObjectField (DataInputStream data, Class type) throws IOException {
      if (type == boolean.class) {
        return new Boolean (data.readByte () != 0);
      } else if (type == byte.class) {
        return new Byte (data.readByte ());
      } else if (type == short.class) {
        return new Short (data.readShort ());
      } else if (type == int.class) {
        return new Integer (data.readInt ());
      } else if (type == long.class) {
        return new Long (data.readLong ());
      } else if (type == float.class) {
        return new Float (data.readFloat ());
      } else if (type == double.class) {
        return new Double (data.readDouble ());
      } else if (type == char.class) {
        return new Character (data.readChar ());
      } else if (type == String.class) {
        return readString (data);
      } else {
        asserttest (false, "primitive type");
        return null;
      }
    }

    private String readString (DataInputStream data) throws IOException {
      byte tc = data.readByte ();
      switch (tc) {
        case TC_NULL:
          return null;
        case TC_REFERENCE:
          int wireHandle = data.readInt ();
          return (String) wireGet (wireHandle - baseWireHandle);
        case TC_STRING:
          String string = data.readUTF ();
          wireOffset (string);
          return string;
        default:
          asserttest (false, "string type");
      }
      return null;
    }
  }

  class Retriever {
    private Object decode (byte[] data) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
      ByteArrayInputStream byteArrayIn = new ByteArrayInputStream (data);
      ObjectInputStream objectInput = new ObjectInputStream (byteArrayIn);
      Object object = objectInput.readObject ();
      return object;
    }
  
    private byte[] encode (RetrievalFields object) throws IOException, ClassNotFoundException {
      ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream ();
      DataOutputStream dataOut = new DataOutputStream (byteArrayOut);
      encode (dataOut, object);
      dataOut.close ();
      byte[] encoded = byteArrayOut.toByteArray ();
      return encoded;
    }

    private int wireOffset = 0;
    private Map wireMap = new HashMap ();

    private void wireReset () {
      wireOffset = 0;
      wireMap.clear ();
    }

    private void wireOffset (Object o) {
      int i = wireOffset ++;
      if (o != null) {
        int h = System.identityHashCode (o);
        Integer ii = new Integer (i);
        Integer hh = new Integer (h);
        wireMap.put (hh, ii);
      }
    }

    private int wireGet (Object o) {
      int h = System.identityHashCode (o);
      Integer hh = new Integer (h);
      Integer ii = (Integer) wireMap.get (hh);
      if (ii == null)
        return -1;
      else
        return ii.intValue ();
    }

    private void encode (DataOutputStream data, RetrievalFields object) throws IOException, ClassNotFoundException {
      data.writeShort (STREAM_MAGIC);
      data.writeShort (STREAM_VERSION);
      wireReset ();
      encodeObject (data, object);
    }
  
    private void encodeObject (DataOutputStream data, RetrievalFields object) throws IOException, ClassNotFoundException {
      data.writeByte (TC_OBJECT);
      String className = object.getClassName ();
      Class clazz = Class.forName (className);
      List fields = new ArrayList (), fields_;
      Map suffixes = new HashMap ();
      ObjectStreamClass osClass;
      do {
        osClass = ObjectStreamClass.lookup (clazz);
        encodeClassDescriptor (data, osClass, fields, suffixes);
        clazz = clazz.getSuperclass ();
      } while (osClass != null);
      wireOffset (null);
      int numClasses = fields.size ();
      for (int i = numClasses - 1; i >= 0; -- i) {
        fields_ = (List) fields.get (i);
        encodeObjectFields (data, object, fields_, suffixes);
      }
    }

    private void encodeClassDescriptor (DataOutputStream data, ObjectStreamClass osClass, List fields, Map suffixes) throws IOException {
      if (osClass == null) {
        data.writeByte (TC_NULL);
        return;
      }
      data.writeByte (TC_CLASSDESC);
      wireOffset (null);
      data.writeUTF (osClass.getName ());
      data.writeLong (osClass.getSerialVersionUID ());
      byte flags = SC_SERIALIZABLE;
      // assert serializable
      // assert !externalizable
      // assert !writeObject
      data.writeByte (flags);
      ObjectStreamField[] osFields = osClass.getFields ();
      int numFields = osFields.length;
      data.writeShort (numFields);
      List list = new ArrayList (numFields);
      for (int i = 0; i < numFields; ++ i) {
        ObjectStreamField osField = osFields[i];
        encodeClassField (data, osField, list, suffixes);
      }
      data.writeByte (TC_ENDBLOCKDATA);
      fields.add (list);
    }

    private void encodeClassField (DataOutputStream data, ObjectStreamField osField, List list, Map suffixes) throws IOException {
      char tc = osField.getTypeCode ();
      Class type = typeOf (tc);
      if ((type == null) && stringSig.equals (osField.getTypeString ()))
        type = String.class;
      asserttest (type != null, "known type");
      data.writeByte (tc);
      String name = osField.getName ();
      data.writeUTF (name);
      if (type == String.class)
        writeString (data, stringSig);
      list.add (name);
      list.add (type);
      if (suffixes.containsKey (name))
        ((StringBuffer) suffixes.get (name)).append ('\'');
      else
        suffixes.put (name, new StringBuffer ());
    }
  
    private void encodeObjectFields (DataOutputStream data, RetrievalFields object, List fields, Map suffixes) throws IOException {
      Iterator iter = fields.iterator ();
      while (iter.hasNext ()) {
        String name = (String) iter.next ();
        Class type = (Class) iter.next ();
        StringBuffer suffix = (StringBuffer) suffixes.get (name);
        Object value = object.getValue (name + suffix, type);
        encodeObjectField (data, type, value);
        int n = suffix.length ();
        if (n > 0)
          suffix.setLength (n - 1);
      }
    }

    private void encodeObjectField (DataOutputStream data, Class type, Object value) throws IOException {
      if (type == boolean.class) {
        data.writeBoolean (((Boolean) value).booleanValue ());
      } else if (type == byte.class) {
        data.writeByte (((Byte) value).byteValue ());
      } else if (type == short.class) {
        data.writeShort (((Short) value).shortValue ());
      } else if (type == int.class) {
        data.writeInt (((Integer) value).intValue ());
      } else if (type == long.class) {
        data.writeLong (((Long) value).longValue ());
      } else if (type == float.class) {
        data.writeFloat (((Float) value).floatValue ());
      } else if (type == double.class) {
        data.writeDouble (((Double) value).doubleValue ());
      } else if (type == char.class) {
        data.writeChar (((Character) value).charValue ());
      } else if (type == String.class) {
        writeString (data, (String) value);
      } else {
        asserttest (false, "primitive type");
      }
    }

    private void writeString (DataOutputStream data, String string) throws IOException {
      if (string == null) {
        data.writeByte (TC_NULL);
      } else {
        int wireHandle = wireGet (string);
        if (wireHandle < 0) {
          data.writeByte (TC_STRING);
          data.writeUTF (string);
          wireOffset (string);
        } else {
          data.writeByte (TC_REFERENCE);
          data.writeInt (wireHandle + baseWireHandle);
        }
      }
    }
  }
}

