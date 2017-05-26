package tetzlaff.gl.vecmath;

/**
 * A vector of four dimensions (for linear algebra calculations) backed by 
 * 32-bit floats.  Useful for homogeneous coordinates in three dimensional
 * space.  This is an immutable object.
 * 
 * @author Michael Tetzlaff
 */
public class Vector4 
{
	/**
	 * The first dimension
	 */
	public final float x;
	/**
	 * The second dimension
	 */
	public final float y;
	/**
	 * The third dimension
	 */
	public final float z;
	/**
	 * The fourth dimension (or heterogeneous coordinate)
	 */
	public final float w;
	
	public static final Vector4 ZERO_DIRECTION = fromVector3AsDirection(Vector3.ZERO);
	public static final Vector4 ZERO_POSITION = fromVector3AsPosition(Vector3.ZERO);
	
	/**
	 * Construct a vector in four dimensions with the given values.
	 * @param x Value of the first dimension.
	 * @param y Value of the second dimension.
	 * @param z Value of the third dimension.
	 * @param w Value of the fourth dimension (or heterogeneous coordinate).
	 */
	private Vector4(float x, float y, float z, float w)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}
	
	public static Vector4 fromScalar(float value)
	{
		return new Vector4(value, value, value, value);
	}
	
	public static Vector4 fromScalars(float x, float y, float z, float w)
	{
		return new Vector4(x, y, z, w);
	}
	
	public static Vector4 fromVector2(Vector3 v2, float z, float w)
	{
		return new Vector4(v2.x, v2.y, z, w);
	}
	
	public static Vector4 fromVector3(Vector3 v3, float w)
	{
		return new Vector4(v3.x, v3.y, v3.z, w);
	}
	
	public static Vector4 fromVector3AsDirection(Vector3 v3)
	{
		return new Vector4(v3.x, v3.y, v3.z, 0.0f);
	}
	
	public static Vector4 fromVector3AsPosition(Vector3 v3)
	{
		return new Vector4(v3.x, v3.y, v3.z, 1.0f);
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (o instanceof Vector4)
		{
			Vector4 other = (Vector4)o;
			return other.x == this.x && other.y == this.y && other.z == this.z && other.w == this.w;
		}
		else
		{
			return false;
		}
	}
	
	/**
	 * Construct a new vector as the sum of this one and the given parameter.
	 * @param other The vector to add.
	 * @return A new vector that is the mathematical (componentwise) sum of this and 'other'.
	 */
	public Vector4 plus(Vector4 other)
	{
		return new Vector4(
			this.x + other.x,
			this.y + other.y,
			this.z + other.z,
			this.w + other.w
		);
	}
	
	/**
	 * Construct a new vector as the subtraction of the given parameter from this.
	 * @param other The vector to add.
	 * @return A new vector that is the mathematical (componentwise) subtraction of 'other' from this.
	 */
	public Vector4 minus(Vector4 other)
	{
		return new Vector4(
			this.x - other.x,
			this.y - other.y,
			this.z - other.z,
			this.w - other.w
		);
	}
	
	/**
	 * Construct a new vector that is the negation of this.
	 * @return A new vector with the values (-x, -y, -z, -w)
	 */
	public Vector4 negated()
	{
		return new Vector4(-this.x, -this.y, -this.z, -this.w);
	}
	
	/**
	 * Construct a new vector that is the product of this and a given scaler.
	 * @param s The scaler to multiply by.
	 * @return A new vector equal to (s*x, s*y, s*z, s*w)
	 */
	public Vector4 times(float s)
	{
		return new Vector4(s*this.x, s*this.y, s*this.z, s*this.w);
	}
	
	/**
	 * Construct a new vector that is the quotient of this and a given scaler.
	 * @param s The scaler to divide by.
	 * @return A new vector equal to (x/s, y/s, z/s, w/s)
	 */
	public Vector4 dividedBy(float s)
	{
		return new Vector4(this.x/s, this.y/s, this.z/s, this.w/s);
	}
	
	/**
	 * Compute the dot product (scaler product) of this vector and another given vector.
	 * @param other The vector to use when computing the dot product.
	 * @return A scaler value equal to the sum of x1*x2, y1*y2, z1*z2 and w1*w2.
	 */
	public float dot(Vector4 other)
	{
		return this.x * other.x + this.y * other.y + this.z * other.z + this.w * other.w;
	}
	
	/**
	 * Compute the outer product of this vector and another given vector.
	 * @param other The vector to use when computing the outer product.
	 * @return The matrix that is the outer product of the vectors.
	 */
	public Matrix4 outerProduct(Vector4 other)
	{
		return Matrix4.fromColumns(
			Vector4.fromScalars(this.x * other.x, this.y * other.x, this.z * other.x, this.w * other.x),
			Vector4.fromScalars(this.x * other.y, this.y * other.y, this.z * other.y, this.w * other.y),
			Vector4.fromScalars(this.x * other.z, this.y * other.z, this.z * other.z, this.w * other.z),
			Vector4.fromScalars(this.x * other.w, this.y * other.w, this.z * other.w, this.w * other.w)
		);
	}
	
	/**
	 * Compute a scaler value representing the length/magnitude of this vector.
	 * @return A scaler value equal to square root of the sum of squares of the components.
	 */
	public float length()
	{
		return (float)Math.sqrt(this.dot(this));
	}
	
	/**
	 * Calculate the distance between this and another given vector.
	 * @param other The vector to compute the distance between.
	 * @return A scaler value equal to the length of the different vector.
	 */
	public float distance(Vector4 other)
	{
		return this.minus(other).length();
	}
	
	/**
	 * Create a new vector with the same direction as this one but with unit
	 * magnitude (a length of 1.0).  CAUTION!  May cause divide by zero error.
	 * Do not attempt to normalize a zero-length vector.
	 * @return A new vector equal to this vector divided by it's length.
	 */
	public Vector4 normalized()
	{
		return this.times(1.0f / this.length());
	}
}
