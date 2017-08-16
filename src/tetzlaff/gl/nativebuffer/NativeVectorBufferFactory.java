package tetzlaff.gl.nativebuffer;

import java.nio.ByteBuffer;

public final class NativeVectorBufferFactory 
{
    private final static NativeVectorBufferFactory INSTANCE = new NativeVectorBufferFactory();

    public static NativeVectorBufferFactory getInstance()
    {
        return INSTANCE;
    }

    public NativeVectorBuffer createEmpty(NativeDataType dataType, int dimensions, int count)
    {
        switch(dataType)
        {
        case BYTE: return new NativeByteVectorBuffer(dimensions, count);
        case UNSIGNED_BYTE: return new NativeUnsignedByteVectorBuffer(dimensions, count);
        case SHORT: return new NativeShortVectorBuffer(dimensions, count);
        case UNSIGNED_SHORT: return new NativeUnsignedShortVectorBuffer(dimensions, count);
        case INT: return new NativeIntVectorBuffer(dimensions, count);
        case UNSIGNED_INT: return new NativeUnsignedIntVectorBuffer(dimensions, count);
        case FLOAT: return new NativeFloatVectorBuffer(dimensions, count);
        case DOUBLE: return new NativeDoubleVectorBuffer(dimensions, count);
        default: throw new IllegalArgumentException("Unrecognized data type " + dataType + ".");
        }
    }

    public NativeVectorBuffer createFromExistingBuffer(NativeDataType dataType, int dimensions, int count, ByteBuffer buffer)
    {
        switch(dataType)
        {
        case BYTE: return new NativeByteVectorBuffer(dimensions, count, buffer);
        case UNSIGNED_BYTE: return new NativeUnsignedByteVectorBuffer(dimensions, count, buffer);
        case SHORT: return new NativeShortVectorBuffer(dimensions, count, buffer);
        case UNSIGNED_SHORT: return new NativeUnsignedShortVectorBuffer(dimensions, count, buffer);
        case INT: return new NativeIntVectorBuffer(dimensions, count, buffer);
        case UNSIGNED_INT: return new NativeUnsignedIntVectorBuffer(dimensions, count, buffer);
        case FLOAT: return new NativeFloatVectorBuffer(dimensions, count, buffer);
        case DOUBLE: return new NativeDoubleVectorBuffer(dimensions, count, buffer);
        default: throw new IllegalArgumentException("Unrecognized data type " + dataType + ".");
        }
    }

    public NativeVectorBuffer createFromByteArray(NativeDataType dataType, int dimensions, int count, byte[] byteArray)
    {
        switch(dataType)
        {
        case BYTE: return new NativeByteVectorBuffer(dimensions, count, byteArray);
        case UNSIGNED_BYTE: return new NativeUnsignedByteVectorBuffer(dimensions, count, byteArray);
        case SHORT: return new NativeShortVectorBuffer(dimensions, count, byteArray);
        case UNSIGNED_SHORT: return new NativeUnsignedShortVectorBuffer(dimensions, count, byteArray);
        case INT: return new NativeIntVectorBuffer(dimensions, count, byteArray);
        case UNSIGNED_INT: return new NativeUnsignedIntVectorBuffer(dimensions, count, byteArray);
        case FLOAT: return new NativeFloatVectorBuffer(dimensions, count, byteArray);
        case DOUBLE: return new NativeDoubleVectorBuffer(dimensions, count, byteArray);
        default: throw new IllegalArgumentException("Unrecognized data type " + dataType + ".");
        }
    }

    public NativeVectorBuffer createFromShortArray(boolean unsigned, int dimensions, int count, short[] shortArray)
    {
        if (!unsigned)
        {
            return new NativeShortVectorBuffer(dimensions, count, shortArray);
        }
        else
        {
            return new NativeUnsignedShortVectorBuffer(dimensions, count, shortArray);
        }
    }

    public NativeVectorBuffer createFromIntArray(boolean unsigned, int dimensions, int count, int[] intArray)
    {
        if (!unsigned)
        {
            return new NativeIntVectorBuffer(dimensions, count, intArray);
        }
        else
        {
            return new NativeUnsignedIntVectorBuffer(dimensions, count, intArray);
        }
    }

    public NativeVectorBuffer createFromFloatArray(int dimensions, int count, float[] floatArray)
    {
        return new NativeFloatVectorBuffer(dimensions, count, floatArray);
    }

    public NativeVectorBuffer createFromDoubleArray(int dimensions, int count, double[] doubleArray)
    {
        return new NativeDoubleVectorBuffer(dimensions, count, doubleArray);
    }
}
