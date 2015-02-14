package tetzlaff.reflacq;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import tetzlaff.gl.PrimitiveMode;
import tetzlaff.gl.helpers.FloatVertexList;
import tetzlaff.gl.helpers.Vector3;
import tetzlaff.gl.opengl.OpenGLContext;
import tetzlaff.gl.opengl.OpenGLFramebufferObject;
import tetzlaff.gl.opengl.OpenGLProgram;
import tetzlaff.gl.opengl.OpenGLRenderable;
import tetzlaff.gl.opengl.OpenGLResource;
import tetzlaff.gl.opengl.OpenGLTextureArray;
import tetzlaff.gl.opengl.OpenGLVertexBuffer;
import tetzlaff.ulf.UnstructuredLightField;
import tetzlaff.window.glfw.GLFWWindow;

public class TexGenProgram
{
	public static void main(String[] args) 
    {
    	Date timestamp;
		
    	OpenGLContext ulfToTexContext = new GLFWWindow(800, 800, "Texture Generation");
    	ulfToTexContext.enableDepthTest();
    	ulfToTexContext.enableBackFaceCulling();
    	
    	int textureSize = 1024;
    	float gamma = 1.0f; // 2.2f;
    	Vector3 guessSpecularColor = new Vector3(1.0f, 1.0f, 1.0f);
    	float guessSpecularWeight = 10.0f;
    	
        try
        {
	    	OpenGLProgram worldToTextureProgram = new OpenGLProgram(new File("shaders\\texspace.vert"), new File("shaders\\projtex.frag"));
    		OpenGLProgram modelFitProgram = new OpenGLProgram(new File("shaders\\texspace.vert"), new File("shaders\\modelfit.frag"));
    		OpenGLProgram specularDebugProgram = new OpenGLProgram(new File("shaders\\texspace.vert"), new File("shaders\\speculardebug.frag"));
    		
    		JFileChooser fileChooser = new JFileChooser(new File("").getAbsolutePath());
    		fileChooser.removeChoosableFileFilter(fileChooser.getAcceptAllFileFilter());
    		fileChooser.setFileFilter(new FileNameExtensionFilter("View Set files (.vset)", "vset"));
    		
    		if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
    		{

    	    	String lightFieldDirectory = fileChooser.getSelectedFile().getParent();
    	    	UnstructuredLightField lightField = UnstructuredLightField.loadFromDirectory(lightFieldDirectory);
    		
	    		lightField.settings.setOcclusionBias(0.005f);
	    		
	    		System.out.println("Projecting light field images into texture space...");
		    	timestamp = new Date();
	    		
	    		OpenGLTextureArray textures = new OpenGLTextureArray(textureSize, textureSize, lightField.viewSet.getCameraPoseCount(), true, false);
		    	OpenGLFramebufferObject worldToTextureFBO = new OpenGLFramebufferObject(textureSize, textureSize, 0, false);
		    	OpenGLRenderable worldToTextureRenderable = new OpenGLRenderable(worldToTextureProgram);
		    	
		    	Iterable<OpenGLResource> worldToTextureVBOResources = worldToTextureRenderable.addVertexMesh("position", "texCoord", "normal", lightField.proxy);
		    	worldToTextureRenderable.program().setTexture("imageTextures", lightField.viewSet.getTextures());
		    	worldToTextureRenderable.program().setTexture("depthTextures", lightField.depthTextures);
		    	worldToTextureRenderable.program().setUniformBuffer("CameraPoses", lightField.viewSet.getCameraPoseBuffer());
		    	worldToTextureRenderable.program().setUniformBuffer("CameraProjections", lightField.viewSet.getCameraProjectionBuffer());
		    	worldToTextureRenderable.program().setUniformBuffer("CameraProjectionIndices", lightField.viewSet.getCameraProjectionIndexBuffer());
		    	worldToTextureRenderable.program().setUniform("occlusionEnabled", lightField.settings.isOcclusionEnabled());
		    	worldToTextureRenderable.program().setUniform("occlusionBias", lightField.settings.getOcclusionBias());
		    	
		    	new File(lightFieldDirectory + "\\output\\debug\\diffuse").mkdirs();
		    	
		    	for (int i = 0; i < lightField.viewSet.getCameraPoseCount(); i++)
		    	{
		    		worldToTextureFBO.setColorAttachment(0, textures.getLayerAsFramebufferAttachment(i));
		    		worldToTextureRenderable.program().setUniform("cameraPoseIndex", i);
		    		
		    		worldToTextureFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
		    		worldToTextureFBO.clearDepthBuffer();
		    		worldToTextureRenderable.draw(PrimitiveMode.TRIANGLES, worldToTextureFBO);
		    		
		    		worldToTextureFBO.saveColorBufferToFile(0, "PNG", String.format(lightFieldDirectory + "\\output\\debug\\diffuse\\%04d.png", i));
		    	}		    	
		    	
		    	worldToTextureFBO.delete();
		    	for (OpenGLResource r : worldToTextureVBOResources)
		    	{
		    		r.delete();
		    	}
		    	
				System.out.println("Projections completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		    	
		    	System.out.println("Fitting to model...");
		    	timestamp = new Date();
		    	
		    	OpenGLRenderable renderable = new OpenGLRenderable(modelFitProgram);
		    	
		    	renderable.addVertexMesh("position", "texCoord", null, lightField.proxy);
		    	renderable.program().setTexture("textures", textures);
		    	renderable.program().setUniform("textureCount", lightField.viewSet.getCameraPoseCount());
		    	renderable.program().setUniformBuffer("CameraPoses", lightField.viewSet.getCameraPoseBuffer());
		    	renderable.program().setUniform("gamma", gamma);
		    	renderable.program().setUniform("guessSpecularColor", guessSpecularColor);
		    	renderable.program().setUniform("guessSpecularWeight", guessSpecularWeight);
		    	
		    	OpenGLFramebufferObject framebuffer = new OpenGLFramebufferObject(textureSize, textureSize, 8, false);
		    	
		    	new File(lightFieldDirectory + "\\output\\textures").mkdirs();
		    	
		    	framebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearColorBuffer(1, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearColorBuffer(2, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearColorBuffer(3, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearColorBuffer(4, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearColorBuffer(5, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearColorBuffer(6, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearColorBuffer(7, 0.0f, 0.0f, 0.0f, 0.0f);
		    	framebuffer.clearDepthBuffer();
		    	
		        renderable.draw(PrimitiveMode.TRIANGLES, framebuffer);
		        
		        framebuffer.saveColorBufferToFile(0, "PNG", lightFieldDirectory + "\\output\\textures\\diffuse.png");
		        framebuffer.saveColorBufferToFile(1, "PNG", lightFieldDirectory + "\\output\\textures\\normal.png");
		        framebuffer.saveColorBufferToFile(2, "PNG", lightFieldDirectory + "\\output\\textures\\specular.png");
		        framebuffer.saveColorBufferToFile(3, "PNG", lightFieldDirectory + "\\output\\textures\\roughness.png");
		        framebuffer.saveColorBufferToFile(4, "PNG", lightFieldDirectory + "\\output\\debug\\ambient.png");
		        framebuffer.saveColorBufferToFile(5, "PNG", lightFieldDirectory + "\\output\\debug\\debug1.png");
		        framebuffer.saveColorBufferToFile(6, "PNG", lightFieldDirectory + "\\output\\debug\\debug2.png");
		        framebuffer.saveColorBufferToFile(7, "PNG", lightFieldDirectory + "\\output\\debug\\debug3.png");

		    	System.out.println("Model fitting completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		    	
		    	System.out.println("Generating specular debug images...");
		    	timestamp = new Date();
	    		
	    		OpenGLTextureArray specularDebugTextures = new OpenGLTextureArray(textureSize, textureSize, lightField.viewSet.getCameraPoseCount(), true, false);
		    	OpenGLFramebufferObject specularDebugFBO = new OpenGLFramebufferObject(textureSize, textureSize, 0, false);
		    	OpenGLRenderable specularDebugRenderable = new OpenGLRenderable(specularDebugProgram);
		    	
		    	Iterable<OpenGLResource> specularDebugResources = specularDebugRenderable.addVertexMesh("position", "texCoord", "normal", lightField.proxy);
		    	specularDebugRenderable.program().setTexture("textures", textures);
		    	specularDebugRenderable.program().setTexture("diffuse", framebuffer.getColorAttachmentTexture(0));
		    	specularDebugRenderable.program().setUniformBuffer("CameraPoses", lightField.viewSet.getCameraPoseBuffer());
		    	specularDebugRenderable.program().setUniform("gamma", gamma);
		    	
		    	new File(lightFieldDirectory + "\\output\\debug\\specular").mkdirs();
		    	
		    	for (int i = 0; i < lightField.viewSet.getCameraPoseCount(); i++)
		    	{
		    		specularDebugFBO.setColorAttachment(0, specularDebugTextures.getLayerAsFramebufferAttachment(i));
		    		specularDebugRenderable.program().setUniform("textureIndex", i);
		    		
		    		specularDebugFBO.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, 0.0f);
		    		specularDebugFBO.clearDepthBuffer();
		    		specularDebugRenderable.draw(PrimitiveMode.TRIANGLES, specularDebugFBO);
		    		
		    		specularDebugFBO.saveColorBufferToFile(0, "PNG", String.format(lightFieldDirectory + "\\output\\debug\\specular\\%04d.png", i));
		    	}		    	
		    	
		    	specularDebugFBO.delete();
		    	for (OpenGLResource r : specularDebugResources)
		    	{
		    		r.delete();
		    	}
		    	
				System.out.println("Specular debug images completed in " + (new Date().getTime() - timestamp.getTime()) + " milliseconds.");
		        
		        textures.delete();
		    	framebuffer.delete();
		        lightField.deleteOpenGLResources();
    		}
    		worldToTextureProgram.delete();
    		modelFitProgram.delete();
        }
        catch (IOException e)
        {
        	e.printStackTrace();
        }
        
        GLFWWindow.closeAllWindows();
        System.exit(0);
	}
}
