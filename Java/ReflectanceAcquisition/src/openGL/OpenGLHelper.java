package openGL;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import openGL.exceptions.OpenGLException;
import openGL.exceptions.OpenGLInvalidEnumException;
import openGL.exceptions.OpenGLInvalidFramebufferOperationException;
import openGL.exceptions.OpenGLInvalidOperationException;
import openGL.exceptions.OpenGLInvalidValueException;
import openGL.exceptions.OpenGLOutOfMemoryException;
import openGL.exceptions.OpenGLStackOverflowException;
import openGL.exceptions.OpenGLStackUnderflowException;

public class OpenGLHelper 
{
	public static void openGLErrorCheck()
	{
		int error = glGetError();
		switch (error)
		{
		case GL_NO_ERROR: return;
		case GL_INVALID_ENUM: throw new OpenGLInvalidEnumException();
		case GL_INVALID_VALUE: throw new OpenGLInvalidValueException();
		case GL_INVALID_OPERATION: throw new OpenGLInvalidOperationException();
		case GL_INVALID_FRAMEBUFFER_OPERATION: throw new OpenGLInvalidFramebufferOperationException();
		case GL_OUT_OF_MEMORY: throw new OpenGLOutOfMemoryException();
		case GL_STACK_UNDERFLOW: throw new OpenGLStackUnderflowException();
		case GL_STACK_OVERFLOW: throw new OpenGLStackOverflowException();
		default: throw new OpenGLException("Unrecognized OpenGL Exception.");
		}
	}
}
