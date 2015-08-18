package tetzlaff.gl.helpers;

/**
 * @author Michael Tetzlaff
 * 
 * A vector of four dimensions (for linear algebra calculations) backed by 
 * 64-bit floats.  Useful for heterogeneous coordinate calculations. This
 * is an immutable object.
 * 
 * @see Vector4
 */
public class DoubleVector4 
{
	public final double x;
	public final double y;
	public final double z;
	public final double w;

	public DoubleVector4(double x, double y, double z, double w)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		this.w = w;
	}
	
	public DoubleVector4(DoubleVector2 v2, double z, double w)
	{
		this(v2.x, v2.y, z, w);
	}
	
	public DoubleVector4(DoubleVector3 v3, double w)
	{
		this(v3.x, v3.y, v3.z, w);
	}
	
	public DoubleVector4 plus(DoubleVector4 other)
	{
		return new DoubleVector4(
			this.x + other.x,
			this.y + other.y,
			this.z + other.z,
			this.w + other.w
		);
	}
	
	public DoubleVector4 minus(DoubleVector4 other)
	{
		return new DoubleVector4(
			this.x - other.x,
			this.y - other.y,
			this.z - other.z,
			this.w - other.w
		);
	}
	
	public DoubleVector4 negated()
	{
		return new DoubleVector4(-this.x, -this.y, -this.z, -this.w);
	}
	
	public DoubleVector4 times(double s)
	{
		return new DoubleVector4(s*this.x, s*this.y, s*this.z, s*this.w);
	}
	
	public DoubleVector4 dividedBy(double s)
	{
		return new DoubleVector4(this.x/s, this.y/s, this.z/s, this.w/s);
	}
	
	public double dot(DoubleVector4 other)
	{
		return this.x * other.x + this.y * other.y + this.z * other.z + this.w * other.w;
	}
	
	public double length()
	{
		return Math.sqrt(this.dot(this));
	}
	
	public double distance(DoubleVector4 other)
	{
		return this.minus(other).length();
	}
	
	public DoubleVector4 normalized()
	{
		return this.times(1.0 / this.length());
	}
}
