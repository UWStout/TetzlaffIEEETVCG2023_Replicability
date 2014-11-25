import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.glfw.GLFW.*;

import java.io.File;
import java.io.IOException;

import openGL.wrappers.implementations.OpenGLFramebufferObject;
import openGL.wrappers.implementations.OpenGLProgram;
import openGL.wrappers.implementations.OpenGLTexture2D;
import openGL.wrappers.interfaces.FramebufferObject;
import openGL.wrappers.interfaces.Program;
import openGL.wrappers.interfaces.Texture;

import org.lwjgl.Sys;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.system.glfw.ErrorCallback;

public class MainProgram 
{
	private long window;
    private Texture texture;
    private Program program;
    private FramebufferObject framebuffer;
    private FramebufferObject framebuffer2;
 
    public void execute() 
    {
    	try
    	{
	        System.out.println("Using LWJGL version " + Sys.getVersion());
	        
	        init();
	        
	        draw();
	        
	        program.delete();
	        framebuffer.delete();
	        glfwDestroyWindow(window);
	    } 
	    finally 
	    {
	        glfwTerminate();
	    }
    }
 
    private void init() 
    {
        int WIDTH = 300;
        int HEIGHT = 300;
 
        // Create an invisible 1x1 window just so that we have a valid OpenGL context
        glfwSetErrorCallback(ErrorCallback.Util.getDefault());
        if ( glfwInit() != GL_TRUE )
            throw new IllegalStateException("Unable to initialize GLFW");
        glfwWindowHint(GLFW_VISIBLE, GL_FALSE);
        window = glfwCreateWindow(1, 1, "", NULL, NULL);
        glfwMakeContextCurrent(window);
        
        GLContext.createFromCurrent();
        System.out.println("Using OpenGL version " + glGetString(GL_VERSION));

        framebuffer = new OpenGLFramebufferObject(WIDTH, HEIGHT);
        framebuffer2 = new OpenGLFramebufferObject(WIDTH, HEIGHT);
        
        try
        {
        	program = new OpenGLProgram(new File("shaders/test.vert"), new File("shaders/test.frag"));
        	program.use();
        }
        catch (IOException e)
        {
        	e.printStackTrace();
        }
        
        try
        {
        	texture = new OpenGLTexture2D(GL_RGBA, "PNG", "checkerboard.png");
        }
        catch (IOException e)
        {
        	e.printStackTrace();
        }
    }
    
    private void draw() 
    {
    	framebuffer.bindForDraw();
    	glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        texture.bindToTextureUnit(0);
        program.setUniform("texture0", 0);
        glBegin(GL_QUADS);
        	glTexCoord2f(0.0f, 0.0f); glVertex3f(-1.0f, -1.0f, 0.0f);
        	glTexCoord2f(0.0f, 1.0f); glVertex3f(-1.0f, 1.0f, 0.0f);
        	glTexCoord2f(1.0f, 1.0f); glVertex3f(1.0f, 1.0f, 0.0f);
        	glTexCoord2f(1.0f, 0.0f); glVertex3f(1.0f, -1.0f, 0.0f);
        glEnd();
        
        try 
        {
            framebuffer.saveToFile(0, "png", "output1.png");
        } 
        catch(IOException e)
        {
        	e.printStackTrace();
        }
        
        framebuffer2.bindForDraw();
    	glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        
        framebuffer.getColorAttachmentTexture(0).bindToTextureUnit(0);
        program.setUniform("texture0", 0);
        glBegin(GL_QUADS);
        	glTexCoord2f(0.0f, 0.0f); glVertex3f(-1.0f, -1.0f, 0.0f);
        	glTexCoord2f(0.0f, 1.0f); glVertex3f(-1.0f, 1.0f, 0.0f);
        	glTexCoord2f(1.0f, 1.0f); glVertex3f(1.0f, 1.0f, 0.0f);
        	glTexCoord2f(1.0f, 0.0f); glVertex3f(1.0f, -1.0f, 0.0f);
        glEnd();
        
        try 
        {
            framebuffer2.saveToFile(0, "png", "output2.png");
        } 
        catch(IOException e)
        {
        	e.printStackTrace();
        }
    }
 
    public static void main(String[] args) 
    {
        new MainProgram().execute();
    }
}
