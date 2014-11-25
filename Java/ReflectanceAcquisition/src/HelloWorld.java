import openGL.wrappers.implementations.OpenGLFramebuffer;
import openGL.wrappers.implementations.OpenGLFramebufferObject;
import openGL.wrappers.implementations.OpenGLProgram;
import openGL.wrappers.interfaces.FramebufferObject;
import openGL.wrappers.interfaces.Program;
import openGL.wrappers.interfaces.Framebuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.Sys;
import org.lwjgl.opengl.*;
import org.lwjgl.system.glfw.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Scanner;

import javax.imageio.ImageIO;

import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.opengl.TextureLoader;
import org.newdawn.slick.util.ResourceLoader;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.glfw.GLFW.*;
 
public class HelloWorld {
	
    private final int WIDTH = 300;
    private final int HEIGHT = 300;

 
    private long window;
    
    private Texture texture;
    private Program program;
    private Framebuffer framebuffer;
 
    public void execute() {
        System.out.println("Hello LWJGL " + Sys.getVersion() + "!");
 
        try 
        {
            init();
            loop();
            program.delete();
            glfwDestroyWindow(window);
        } 
        finally 
        {
            glfwTerminate();
        }
    }
 
    private void init() {
        glfwSetErrorCallback(ErrorCallback.Util.getDefault());
 
        if ( glfwInit() != GL11.GL_TRUE )
            throw new IllegalStateException("Unable to initialize GLFW");
 
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GL_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GL_TRUE);
        
        window = glfwCreateWindow(WIDTH, HEIGHT, "Hello World!", NULL, NULL);
        if ( window == NULL )
            throw new RuntimeException("Failed to create the GLFW window");
 
        WindowCallback.set(window, new WindowCallbackAdapter() {
            @Override
            public void key(long window, int key, int scancode, int action, int mods) {
                if ( key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE )
                    glfwSetWindowShouldClose(window, GL_TRUE);
            }
        });
 
        ByteBuffer vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        glfwSetWindowPos(
            window,
            (GLFWvidmode.width(vidmode) - WIDTH) / 2,
            (GLFWvidmode.height(vidmode) - HEIGHT) / 2
        );
 
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
 
        glfwShowWindow(window);
        
        GLContext.createFromCurrent();
        System.out.println("Hello OpenGL " + glGetString(GL_VERSION) + "!");

        framebuffer = OpenGLFramebuffer.defaultFramebuffer();
        
        try
        {
        	program = new OpenGLProgram(new File("shaders/test.vert"), new File("shaders/test.frag"));
        	program.use();
        }
        catch (IOException e)
        {
        	e.printStackTrace();
        }
        
        glEnable(GL_TEXTURE_2D);
        
        try
        {
        	texture = TextureLoader.getTexture("PNG", ResourceLoader.getResourceAsStream("checkerboard.png"));
        }
        catch (IOException e)
        {
        	e.printStackTrace();
        }
    }
    
    private void loop() {
        while ( glfwWindowShouldClose(window) == GL_FALSE ) {
        	
        	framebuffer.bindForDraw(0, 0, WIDTH, HEIGHT);
        	glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            
            glActiveTexture(GL_TEXTURE0);
            texture.bind();
            program.setUniform("texture0", 0);
            glBegin(GL_QUADS);
            	glTexCoord2f(0.0f, 0.0f); glVertex3f(-1.0f, -1.0f, 0.0f);
            	glTexCoord2f(0.0f, 1.0f); glVertex3f(-1.0f, 1.0f, 0.0f);
            	glTexCoord2f(1.0f, 1.0f); glVertex3f(1.0f, 1.0f, 0.0f);
            	glTexCoord2f(1.0f, 0.0f); glVertex3f(1.0f, -1.0f, 0.0f);
            glEnd();
 
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
 
    public static void main(String[] args) {
        new HelloWorld().execute();
    }
}