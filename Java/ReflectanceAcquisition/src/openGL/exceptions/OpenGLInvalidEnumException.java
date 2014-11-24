package openGL.exceptions;

public class OpenGLInvalidEnumException extends OpenGLException 
{
	public OpenGLInvalidEnumException() 
	{
		super("An unacceptable value is specified for an enumerated argument.");
	}
}
