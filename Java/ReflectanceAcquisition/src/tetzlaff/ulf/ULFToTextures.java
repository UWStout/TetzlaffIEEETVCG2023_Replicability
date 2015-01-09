package tetzlaff.ulf;

import java.io.File;
import java.io.IOException;

import tetzlaff.gl.FramebufferSize;
import tetzlaff.gl.PrimitiveMode;
import tetzlaff.gl.helpers.Drawable;
import tetzlaff.gl.helpers.Matrix4;
import tetzlaff.gl.helpers.Trackball;
import tetzlaff.gl.helpers.Vector3;
import tetzlaff.gl.opengl.OpenGLContext;
import tetzlaff.gl.opengl.OpenGLDefaultFramebuffer;
import tetzlaff.gl.opengl.OpenGLFramebuffer;
import tetzlaff.gl.opengl.OpenGLProgram;
import tetzlaff.gl.opengl.OpenGLRenderable;
import tetzlaff.gl.opengl.OpenGLResource;

public class ULFToTextures implements ULFDrawable
{
    private static OpenGLProgram genTexProgram;
    
    private String lightFieldDirectory;
    private UnstructuredLightField lightField;
	private OpenGLContext context;
    private OpenGLRenderable renderable;
    private Trackball trackball;
    private ULFLoadedCallback callback;
    private Iterable<OpenGLResource> vboResources;

    public ULFToTextures(OpenGLContext context, String lightFieldDirectory, Trackball trackball)
    {
    	this.context = context;
    	this.lightFieldDirectory = lightFieldDirectory;
    	this.trackball = trackball;
    }

    @Override
    public UnstructuredLightField getLightField()
    {
    	return this.lightField;
    }
    
    @Override
    public void setOnLoadCallback(ULFLoadedCallback callback)
    {
    	this.callback = callback;
    }
 
    @Override
    public void initialize() 
    {
    	if (ULFToTextures.genTexProgram == null)
    	{
	    	try
	        {
	    		ULFToTextures.genTexProgram = new OpenGLProgram(new File("shaders/ulfTex.vert"), new File("shaders/ulfTex.frag"));
	        }
	        catch (IOException e)
	        {
	        	e.printStackTrace();
	        }
    	}
    	
    	try 
    	{
			this.lightField = UnstructuredLightField.loadFromDirectory(this.lightFieldDirectory);
			this.callback.ulfLoaded();
	    	
	    	this.renderable = new OpenGLRenderable(genTexProgram);
	    	this.vboResources = this.renderable.addVertexMesh("position", "texCoord", "normal", lightField.proxy);
		} 
    	catch (IOException e) 
    	{
			e.printStackTrace();
		}
    }

	@Override
	public void update()
	{
	}
    
    @Override
    public void draw() 
    {
    	OpenGLFramebuffer framebuffer = OpenGLDefaultFramebuffer.fromContext(context);
    	
    	FramebufferSize size = framebuffer.getSize();
    	
    	framebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 1.0f);
    	framebuffer.clearDepthBuffer();

    	this.renderable.program().setTexture("imageTextures", lightField.viewSet.getTextures());
    	this.renderable.program().setUniformBuffer("CameraPoses", lightField.viewSet.getCameraPoseBuffer());
    	this.renderable.program().setUniformBuffer("CameraProjections", lightField.viewSet.getCameraProjectionBuffer());
    	this.renderable.program().setUniformBuffer("CameraProjectionIndices", lightField.viewSet.getCameraProjectionIndexBuffer());
    	this.renderable.program().setUniform("cameraPoseCount", lightField.viewSet.getCameraPoseCount());
    	this.renderable.program().setTexture("depthTextures", lightField.depthTextures);
    	
    	renderable.program().setUniform("model_view", 
			Matrix4.lookAt(
				new Vector3(0.0f, 0.0f, 5.0f / trackball.getScale()), 
				new Vector3(0.0f, 0.0f, 0.0f),
				new Vector3(0.0f, 1.0f, 0.0f)
			) // View
			.times(trackball.getRotationMatrix())
			.times(Matrix4.translate(lightField.proxy.getCentroid().negated())) // Model
		);
    	
    	renderable.program().setUniform("projection", Matrix4.perspective((float)Math.PI / 4, (float)size.width / (float)size.height, 0.01f, 100.0f));
    	
    	renderable.program().setUniform("gamma", this.lightField.settings.getGamma());
    	renderable.program().setUniform("weightExponent", this.lightField.settings.getWeightExponent());
    	renderable.program().setUniform("occlusionEnabled", this.lightField.settings.isOcclusionEnabled());
    	renderable.program().setUniform("occlusionBias", this.lightField.settings.getOcclusionBias());
    	
        renderable.draw(PrimitiveMode.TRIANGLES, framebuffer);
    }
    
    @Override
    public void cleanup()
    {
    	for (OpenGLResource r : vboResources)
    	{
    		r.delete();
    	}
    	
        lightField.deleteOpenGLResources();
    }
}
