package tetzlaff.ulf;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import tetzlaff.gl.ColorFormat;
import tetzlaff.gl.Context;
import tetzlaff.gl.Framebuffer;
import tetzlaff.gl.FramebufferObject;
import tetzlaff.gl.FramebufferSize;
import tetzlaff.gl.PrimitiveMode;
import tetzlaff.gl.Program;
import tetzlaff.gl.Renderable;
import tetzlaff.gl.ShaderType;
import tetzlaff.gl.Texture2D;
import tetzlaff.gl.Texture3D;
import tetzlaff.gl.TextureWrapMode;
import tetzlaff.gl.ColorFormat.DataType;
import tetzlaff.gl.builders.ColorTextureBuilder;
import tetzlaff.gl.builders.framebuffer.ColorAttachmentSpec;
import tetzlaff.gl.builders.framebuffer.DepthAttachmentSpec;
import tetzlaff.gl.helpers.CameraController;
import tetzlaff.gl.helpers.DoubleVector3;
import tetzlaff.gl.helpers.FloatVertexList;
import tetzlaff.gl.helpers.Matrix3;
import tetzlaff.gl.helpers.Matrix4;
import tetzlaff.gl.helpers.Vector3;
import tetzlaff.gl.helpers.Vector4;
import tetzlaff.gl.helpers.VertexMesh;
import tetzlaff.helpers.EnvironmentMap;

public class ULFRenderer<ContextType extends Context<ContextType>> implements ULFDrawable<ContextType>
{
    private Program<ContextType> program;
    private Program<ContextType> viewIndexProgram;
    
    private File cameraFile;
    private File geometryFile;
    private ULFLoadOptions loadOptions;
    private UnstructuredLightField<ContextType> lightField;
	private ContextType context;
    private Renderable<ContextType> mainRenderable;
    private Renderable<ContextType> indexRenderable;
    private CameraController cameraController;
    private ULFLoadingMonitor callback;

    private Vector3 clearColor;
    private boolean viewIndexCacheEnabled;
    private boolean halfResEnabled;
    FramebufferObject<ContextType> indexFBO;
    private Texture3D<ContextType> viewIndexCacheTexturesFront;
    private Texture3D<ContextType> viewIndexCacheTexturesBack;
    private Program<ContextType> simpleTexProgram;
    private Renderable<ContextType> simpleTexRenderable;
    
    private File newEnvironmentFile = null;
    private boolean environmentTextureEnabled;
    private Texture2D<ContextType> environmentTexture;
    private Matrix4 envMapMatrix = null;

    private Program<ContextType> environmentBackgroundProgram;
    private Renderable<ContextType> environmentBackgroundRenderable;
    
    private float targetFPS;
    private long lastFrame = 0;
    private int offset=0, stride=0;

    private boolean resampleRequested;
    private int resampleWidth, resampleHeight;
    private File resampleVSETFile;
    private File resampleExportPath;
    
	private Consumer<Matrix4> resampleSetupCallback;
	private Runnable resampleCompleteCallback;

	private boolean fidelityRequested;
    private File fidelityExportPath;
    
	private Consumer<Integer> fidelitySetupCallback;
	private Runnable fidelityCompleteCallback;
    
    private boolean multisamplingEnabled = false;

    public ULFRenderer(ContextType context, Program<ContextType> program, Program<ContextType> viewIndexProgram, File cameraFile, File meshFile, ULFLoadOptions loadOptions, CameraController cameraController)
    {
    	this.context = context;
    	this.program = program;
    	this.viewIndexProgram = program;
    	this.cameraFile = cameraFile;
    	this.geometryFile = meshFile;
    	this.loadOptions = loadOptions;
    	this.cameraController = cameraController;
    	this.viewIndexCacheEnabled = false;
    	this.targetFPS = 30.0f;
    	this.clearColor = new Vector3(0.0f);
    }
	
	public void setCameraController(CameraController cameraController)
	{
		this.cameraController = cameraController;
	}
    
    @Override
    public void setOnLoadCallback(ULFLoadingMonitor callback)
    {
    	this.callback = callback;
    }
    
    @Override
    public void reloadHelperShaders()
    {
    	try
        {
			Program<ContextType> newProgram = context.getShaderProgramBuilder()
    				.addShader(ShaderType.VERTEX, new File("shaders/common/texture.vert"))
    				.addShader(ShaderType.FRAGMENT, new File("shaders/common/envbackgroundtexture.frag"))
    				.createProgram();

			this.environmentBackgroundProgram.delete();
    		this.environmentBackgroundProgram = newProgram;
    		
	    	this.environmentBackgroundRenderable = context.createRenderable(environmentBackgroundProgram);
	    	this.environmentBackgroundRenderable.addVertexBuffer("position", context.createRectangle());
        }
        catch (IOException e)
        {
        	e.printStackTrace();
        }
    }
 
    @Override
    public void initialize() 
    {
    	if (this.program == null)
    	{
	    	try
	        {
	    		this.program = context.getShaderProgramBuilder()
	    				.addShader(ShaderType.VERTEX, new File("shaders/ibr/ulr.vert"))
	    				.addShader(ShaderType.FRAGMENT, new File("shaders/ibr/ulr.frag"))
	    				.createProgram();
	        }
	        catch (IOException e)
	        {
	        	e.printStackTrace();
	        }
    	}

    	if (this.simpleTexProgram == null)
    	{
	    	try
	        {
	    		this.simpleTexProgram = context.getShaderProgramBuilder()
	    				.addShader(ShaderType.VERTEX, new File("shaders/common/texture.vert"))
	    				.addShader(ShaderType.FRAGMENT, new File("shaders/common/texture.frag"))
	    				.createProgram();
	        }
	        catch (IOException e)
	        {
	        	e.printStackTrace();
	        }
    	}
		
    	if (this.environmentBackgroundProgram == null)
    	{
	    	try
	        {
	    		this.environmentBackgroundProgram = context.getShaderProgramBuilder()
	    				.addShader(ShaderType.VERTEX, new File("shaders/common/texture.vert"))
	    				.addShader(ShaderType.FRAGMENT, new File("shaders/common/envbackgroundtexture.frag"))
	    				.createProgram();
	        }
	        catch (IOException e)
	        {
	        	e.printStackTrace();
	        }
    	}
    	
    	try 
    	{
    		if (this.cameraFile.getName().toUpperCase().endsWith(".XML"))
    		{
    			this.lightField = UnstructuredLightField.loadFromAgisoftXMLFile(this.cameraFile, this.geometryFile, this.loadOptions, this.context);
    		}
    		else
    		{
    			this.lightField = UnstructuredLightField.loadFromVSETFile(this.cameraFile, this.loadOptions, this.context);
    			if (this.geometryFile == null)
				{
    				this.geometryFile = lightField.viewSet.getGeometryFile();
				}
    		}
	    	
	    	this.mainRenderable = context.createRenderable(program);
	    	this.mainRenderable.addVertexBuffer("position", this.lightField.positionBuffer);
	    	
	    	if (this.lightField.normalBuffer != null)
	    	{
	    		this.mainRenderable.addVertexBuffer("normal", this.lightField.normalBuffer);
	    	}
	    	
	    	if (this.lightField.texCoordBuffer != null)
	    	{
	    		this.mainRenderable.addVertexBuffer("texCoord", this.lightField.texCoordBuffer);
	    	}
	    	
	    	if (this.lightField.tangentBuffer != null)
	    	{
	    		this.mainRenderable.addVertexBuffer("tangent", this.lightField.tangentBuffer);
	    	}
	    	
	    	if(viewIndexProgram != null)
	    	{
		    	this.indexRenderable = context.createRenderable(viewIndexProgram);
		    	this.indexRenderable.addVertexBuffer("position", this.lightField.positionBuffer);
		    	
		    	if (this.lightField.normalBuffer != null)
		    	{
		    		this.indexRenderable.addVertexBuffer("normal", this.lightField.normalBuffer);
		    	}
		    	
		    	if (this.lightField.texCoordBuffer != null)
		    	{
		    		this.indexRenderable.addVertexBuffer("texCoord", this.lightField.texCoordBuffer);
		    	}
	    	}
	    				
	    	this.simpleTexRenderable = context.createRenderable(simpleTexProgram);
	    	this.simpleTexRenderable.addVertexBuffer("position", context.createRectangle());
			
	    	this.environmentBackgroundRenderable = context.createRenderable(environmentBackgroundProgram);
	    	this.environmentBackgroundRenderable.addVertexBuffer("position", context.createRectangle());
	    	
        	indexFBO = context.getFramebufferObjectBuilder(this.lightField.depthTextures.getWidth(), this.lightField.depthTextures.getHeight())
					.addEmptyColorAttachments(2)
					.createFramebufferObject();
			
			viewIndexCacheTexturesFront = context.get2DColorTextureArrayBuilder(this.lightField.depthTextures.getWidth(), this.lightField.depthTextures.getHeight(), 2)
					.setInternalFormat(ColorFormat.RGBA16I)
					.setMipmapsEnabled(false)
					.setLinearFilteringEnabled(false)
					.setMultisamples(1, true)
					.createTexture();
			
			viewIndexCacheTexturesBack = context.get2DColorTextureArrayBuilder(this.lightField.depthTextures.getWidth(), this.lightField.depthTextures.getHeight(), 2)
					.setInternalFormat(ColorFormat.RGBA16I)
					.setMipmapsEnabled(false)
					.setLinearFilteringEnabled(false)
					.setMultisamples(1, true)
					.createTexture();
			
			indexFBO.setColorAttachment(0, viewIndexCacheTexturesFront.getLayerAsFramebufferAttachment(0));
			indexFBO.setColorAttachment(1, viewIndexCacheTexturesFront.getLayerAsFramebufferAttachment(1));
			indexFBO.clearIntegerColorBuffer(0, -1, -1, -1, -1);
			indexFBO.clearIntegerColorBuffer(1, -1, -1, -1, -1);
			context.flush();
    		
			if (this.callback != null)
			{
				this.callback.loadingComplete();
			}
		} 
    	catch (IOException e) 
    	{
			e.printStackTrace();
		}
    }

	@Override
	public void update()
	{
		if (this.newEnvironmentFile != null)
		{
			File environmentFile = this.newEnvironmentFile;
			this.newEnvironmentFile = null;
			
			try
			{
				System.out.println("Loading new environment texture.");
				
				ColorTextureBuilder<ContextType, ? extends Texture2D<ContextType>> textureBuilder;
				
				if(environmentFile.getName().endsWith("_zvc.hdr"))
				{
					// Use Michael Ludwig's code to convert the cross to a cube map to a panorama
					EnvironmentMap envMap = EnvironmentMap.createFromHDRFile(environmentFile);
					float[] pixels = EnvironmentMap.toPanorama(envMap.getData(), envMap.getSide(), envMap.getSide() * 4, envMap.getSide() * 2);
					FloatVertexList pixelList = new FloatVertexList(3, pixels.length / 3, pixels);
					textureBuilder = context.get2DColorTextureBuilder(envMap.getSide() * 4, envMap.getSide() * 2, pixelList);
					textureBuilder.setInternalFormat(ColorFormat.RGB32F);
				}
				else
				{
					// Load the panorama directly
					textureBuilder = context.get2DColorTextureBuilder(environmentFile, true);
					if (environmentFile.getName().endsWith(".hdr"))
					{
						textureBuilder.setInternalFormat(ColorFormat.RGB32F);
					}
					else
					{
						textureBuilder.setInternalFormat(ColorFormat.RGB8);
					}
				}
				
				Texture2D<ContextType> newEnvironmentTexture = 
					textureBuilder
						.setMipmapsEnabled(true)
						.setLinearFilteringEnabled(true)
						.createTexture();
				newEnvironmentTexture.setTextureWrap(TextureWrapMode.Repeat, TextureWrapMode.None);
	
				if (this.environmentTexture != null)
				{
					this.environmentTexture.delete();
				}
				
				this.environmentTexture = newEnvironmentTexture;
			}
			catch (Exception e) 
    		{
				e.printStackTrace();
			}
		}
		
    	if (this.resampleRequested)
    	{
    		try
    		{
				this.resample();
			} 
    		catch (Exception e) 
    		{
				e.printStackTrace();
			}
    		this.resampleRequested = false;
    		if (this.callback != null)
			{
				this.callback.loadingComplete();
			}
    	}
    	else if (this.fidelityRequested)
    	{
    		try
    		{
				this.generateFidelityDiffs();
			} 
    		catch (Exception e) 
    		{
				e.printStackTrace();
			}
    		this.fidelityRequested = false;
    		if (this.callback != null)
			{
				this.callback.loadingComplete();
			}
    	}
	}
	
	private void setupForDraw()
	{
		this.setupForDraw(this.mainRenderable.program());
	}
	
	public void setupForDraw(Program<ContextType> program)
	{
		program.setTexture("viewImages", lightField.viewSet.getTextures());
		program.setUniformBuffer("CameraPoses", lightField.viewSet.getCameraPoseBuffer());
		program.setUniformBuffer("CameraProjections", lightField.viewSet.getCameraProjectionBuffer());
		program.setUniformBuffer("CameraProjectionIndices", lightField.viewSet.getCameraProjectionIndexBuffer());
    	if (lightField.viewSet.getLightPositionBuffer() != null && lightField.viewSet.getLightIntensityBuffer() != null && lightField.viewSet.getLightIndexBuffer() != null)
    	{
    		program.setUniformBuffer("LightPositions", lightField.viewSet.getLightPositionBuffer());
    		program.setUniformBuffer("LightIntensities", lightField.viewSet.getLightIntensityBuffer());
    		program.setUniformBuffer("LightIndices", lightField.viewSet.getLightIndexBuffer());
    	}
    	program.setUniform("viewCount", lightField.viewSet.getCameraPoseCount());
    	program.setUniform("infiniteLightSources", true /* TODO */);
    	if (lightField.depthTextures != null)
		{
    		program.setTexture("depthImages", lightField.depthTextures);
		}
    	
    	program.setUniform("gamma", this.lightField.settings.getGamma());
    	program.setUniform("weightExponent", this.lightField.settings.getWeightExponent());
    	program.setUniform("occlusionEnabled", this.lightField.depthTextures != null && this.lightField.settings.isOcclusionEnabled());
    	program.setUniform("occlusionBias", this.lightField.settings.getOcclusionBias());
    	
    	program.setTexture("luminanceMap", this.lightField.viewSet.getLuminanceMap());

    	program.setUniform("skipViewEnabled", false);
    	program.setUniform("skipView", -1);
	}
	
	public Matrix4 getModelViewMatrix()
	{
    	float scale = new Vector3(lightField.viewSet.getCameraPose(0).times(new Vector4(lightField.proxy.getCentroid(), 1.0f))).length();
		
		return Matrix4.scale(scale)
    			.times(cameraController.getViewMatrix())
    			.times(Matrix4.scale(1.0f / scale))
    			.times(new Matrix4(new Matrix3(lightField.viewSet.getCameraPose(0))))
    			.times(Matrix4.translate(lightField.proxy.getCentroid().negated()));
	}
	
	public Matrix4 getProjectionMatrix()
	{
    	FramebufferSize size = context.getDefaultFramebuffer().getSize();
		float scale = new Vector3(lightField.viewSet.getCameraPose(0).times(new Vector4(lightField.proxy.getCentroid(), 1.0f))).length();
		
		return Matrix4.perspective(
    			//(float)(1.0),
    			lightField.viewSet.getCameraProjection(0).getVerticalFieldOfView(), 
    			(float)size.width / (float)size.height, 
    			0.01f * scale, 100.0f * scale);
	}
    
    @Override
    public void draw()
    {
    	if (offset == stride)
    	{
    		offset = 0;
    		long now = new Date().getTime();
    		
    		if (lastFrame > 0)
    		{
	    		long elapsedTime = now-lastFrame;
	    		float fps = (float)stride/(float)elapsedTime*1000;
	    		stride = Math.max(1, Math.min((int)Math.ceil(stride*targetFPS/fps), lightField.viewSet.getCameraPoseCount()));
	    		//System.out.println("fps="+fps);
	    		//System.out.println("new stride="+stride);
    		}
    		else
    		{
    			stride = lightField.viewSet.getCameraPoseCount();
    		}
    		
    		lastFrame = now;
    	}
    	
    	if(multisamplingEnabled)
		{
			context.enableMultisampling();
		}
		else
		{
			context.disableMultisampling();			
		}
    	
    	context.enableBackFaceCulling();
    	
    	Framebuffer<ContextType> framebuffer = context.getDefaultFramebuffer();
    	FramebufferSize size = framebuffer.getSize();
    	
    	this.setupForDraw();
    	

    	Matrix4 modelView, projection;
    	
    	mainRenderable.program().setUniform("model_view", modelView = getModelViewMatrix());
    	mainRenderable.program().setUniform("projection", projection = getProjectionMatrix());
    	
        FramebufferObject<ContextType> offscreenFBO;
        
        int fboWidth, fboHeight;
		if (halfResEnabled)
		{
			fboWidth = size.width / 2;
			fboHeight = size.height / 2;
		}
		else
		{
			fboWidth = size.width;
			fboHeight = size.height;
		}
		
        if (viewIndexCacheEnabled && viewIndexProgram != null)
		{
        	// Update view indices
			
        	this.indexRenderable.program().setUniform("offset", offset);
        	this.indexRenderable.program().setUniform("stride", stride);
        	
        	this.indexRenderable.program().setUniformBuffer("CameraPoses", lightField.viewSet.getCameraPoseBuffer());
        	this.indexRenderable.program().setUniformBuffer("CameraProjections", lightField.viewSet.getCameraProjectionBuffer());
        	this.indexRenderable.program().setUniformBuffer("CameraProjectionIndices", lightField.viewSet.getCameraProjectionIndexBuffer());
        	this.indexRenderable.program().setUniformBuffer("LightPositions", lightField.viewSet.getLightPositionBuffer());
        	this.indexRenderable.program().setUniformBuffer("LightIntensities", lightField.viewSet.getLightIntensityBuffer());
        	this.indexRenderable.program().setUniformBuffer("LightIndices", lightField.viewSet.getLightIndexBuffer());
        	this.indexRenderable.program().setUniform("cameraPoseCount", lightField.viewSet.getCameraPoseCount());
        	
        	indexRenderable.program().setUniform("model_view", 
    			 cameraController.getViewMatrix() // View
 					.times(Matrix4.scale(1.0f / lightField.proxy.getBoundingRadius()))
    			 	.times(Matrix4.translate(lightField.proxy.getCentroid().negated())) // Model
    		);
        	indexRenderable.program().setUniform("weightExponent", this.lightField.settings.getWeightExponent());
        	
        	indexFBO.setColorAttachment(0, viewIndexCacheTexturesBack.getLayerAsFramebufferAttachment(0));
			indexFBO.setColorAttachment(1, viewIndexCacheTexturesBack.getLayerAsFramebufferAttachment(1));

			viewIndexProgram.setTexture("viewIndexTextures", viewIndexCacheTexturesFront);
			
			indexFBO.clearIntegerColorBuffer(0, -1, -1, -1, -1);
			indexFBO.clearIntegerColorBuffer(1, -1, -1, -1, -1);
			indexRenderable.draw(PrimitiveMode.TRIANGLES, indexFBO);
			context.flush();
				
    		Texture3D<ContextType> tmp = viewIndexCacheTexturesFront;
        	viewIndexCacheTexturesFront = viewIndexCacheTexturesBack;
        	viewIndexCacheTexturesBack = tmp;
        	
			program.setTexture("viewIndexTextures", viewIndexCacheTexturesFront);
		}
    	
    	if(halfResEnabled) 
    	{
			// Do first pass at half resolution to off-screen buffer
			offscreenFBO = context.getFramebufferObjectBuilder(fboWidth, fboHeight)
					.addColorAttachment(new ColorAttachmentSpec(ColorFormat.RGB8)
						.setLinearFilteringEnabled(true))
					.addDepthAttachment(new DepthAttachmentSpec(32, false))
					.createFramebufferObject();
			
			offscreenFBO.clearColorBuffer(0, clearColor.x, clearColor.y, clearColor.z, 1.0f);
	    	offscreenFBO.clearDepthBuffer();
	        mainRenderable.draw(PrimitiveMode.TRIANGLES, offscreenFBO);
	        context.flush();
	        
	        // Second pass at full resolution to default framebuffer
	    	simpleTexRenderable.program().setTexture("tex", offscreenFBO.getColorAttachmentTexture(0));    	

			framebuffer.clearDepthBuffer();
	    	simpleTexRenderable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);

	    	context.flush();
	    	offscreenFBO.delete();
    	} 
    	else 
    	{
    		framebuffer.clearColorBuffer(0, clearColor.x, clearColor.y, clearColor.z, 1.0f);
    		
    		if (environmentTexture != null && environmentTextureEnabled)
			{
				environmentBackgroundProgram.setUniform("useEnvironmentTexture", true);
				environmentBackgroundProgram.setTexture("env", environmentTexture);
				environmentBackgroundProgram.setUniform("model_view", modelView);
				environmentBackgroundProgram.setUniform("projection", projection);
				environmentBackgroundProgram.setUniform("envMapMatrix", envMapMatrix == null ? Matrix4.identity() : envMapMatrix);

				environmentBackgroundProgram.setUniform("gamma", 
						environmentTexture.isInternalFormatCompressed() || 
						environmentTexture.getInternalUncompressedColorFormat().dataType != DataType.FLOATING_POINT 
						? 1.0f : 2.2f);
				
				context.disableDepthTest();
				this.environmentBackgroundRenderable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
				context.enableDepthTest();
			}
    		
    		framebuffer.clearDepthBuffer();
	        mainRenderable.draw(PrimitiveMode.TRIANGLES, framebuffer);  
	        context.flush();
    	}
    	
    	offset++;
    }
    
    @Override
    public void cleanup()
    {
    	lightField.deleteOpenGLResources();
    	
		if (indexFBO != null)
		{
			indexFBO.delete();
			indexFBO = null;
		}
		
    	if (viewIndexCacheTexturesFront != null)
    	{
    		viewIndexCacheTexturesFront.delete();
    		viewIndexCacheTexturesFront = null;
    	}
		
		if (viewIndexCacheTexturesBack != null)
    	{
			viewIndexCacheTexturesBack.delete();
			viewIndexCacheTexturesBack = null;
    	}
    }
	
	@Override
	public VertexMesh getActiveProxy()
	{
		return this.lightField.proxy;
	}
	
	@Override
	public ViewSet<ContextType> getActiveViewSet()
	{
		return this.lightField.viewSet;
	}
    
    public File getVSETFile()
    {
    	return this.cameraFile;
    }
    
    public File getGeometryFile()
    {
    	return this.geometryFile;
    }
    
    public UnstructuredLightField<ContextType> getLightField()
    {
    	return this.lightField;
    }

	@Override
	public float getGamma() 
	{
		return this.lightField.settings.getGamma();
	}

	@Override
	public float getWeightExponent() 
	{
		return this.lightField.settings.getWeightExponent();
	}
	
	@Override
	public boolean isOcclusionEnabled() 
	{
		return this.lightField.settings.isOcclusionEnabled();
	}

	@Override
	public float getOcclusionBias() 
	{
		return this.lightField.settings.getOcclusionBias();
	}

	@Override
	public void setGamma(float gamma) 
	{
		this.lightField.settings.setGamma(gamma);
	}

	@Override
	public void setWeightExponent(float weightExponent) 
	{
		this.lightField.settings.setWeightExponent(weightExponent);
	}

	@Override
	public void setOcclusionEnabled(boolean occlusionEnabled) 
	{
		this.lightField.settings.setOcclusionEnabled(occlusionEnabled);
	}

	@Override
	public void setOcclusionBias(float occlusionBias) 
	{
		this.lightField.settings.setOcclusionBias(occlusionBias);
	}
	
	@Override
	public String toString()
	{
		return this.lightField.toString();
	}
	
	@Override
	public void requestResample(int width, int height, File targetVSETFile, File exportPath) throws IOException
	{
		this.resampleRequested = true;
		this.resampleWidth = width;
		this.resampleHeight = height;
		this.resampleVSETFile = targetVSETFile;
		this.resampleExportPath = exportPath;
	}
	
	public void drawIntoFramebuffer(FramebufferObject<ContextType> framebuffer)
	{
		mainRenderable.draw(PrimitiveMode.TRIANGLES, framebuffer);
	}
	
	private void resample() throws IOException
	{
		ViewSet<ContextType> targetViewSet = ViewSet.loadFromVSETFile(resampleVSETFile, new ViewSetImageOptions(null, false, false, false), context);
		FramebufferObject<ContextType> framebuffer = context.getFramebufferObjectBuilder(resampleWidth, resampleHeight)
				.addColorAttachment()
				.addDepthAttachment()
				.createFramebufferObject();
    	
    	this.setupForDraw();
    	
		for (int i = 0; i < targetViewSet.getCameraPoseCount(); i++)
		{
	    	mainRenderable.program().setUniform("model_view", targetViewSet.getCameraPose(i));
	    	mainRenderable.program().setUniform("projection", 
    			targetViewSet.getCameraProjection(targetViewSet.getCameraProjectionIndex(i))
    				.getProjectionMatrix(targetViewSet.getRecommendedNearPlane(), targetViewSet.getRecommendedFarPlane()));
	    	
	    	this.resampleSetupCallback.accept(targetViewSet.getCameraPose(i));
	    	
	    	framebuffer.clearColorBuffer(0, 0.0f, 0.0f, 0.0f, /*1.0f*/0.0f);
	    	framebuffer.clearDepthBuffer();
	    	
	    	mainRenderable.draw(PrimitiveMode.TRIANGLES, framebuffer);
	    	
	    	File exportFile = new File(resampleExportPath, targetViewSet.getImageFileName(i));
	    	exportFile.getParentFile().mkdirs();
	        framebuffer.saveColorBufferToFile(0, "PNG", exportFile);
	        
	        if (this.callback != null)
	        {
	        	this.callback.setProgress((double) i / (double) targetViewSet.getCameraPoseCount());
	        }
		}
		
		this.resampleCompleteCallback.run();
		
		Files.copy(resampleVSETFile.toPath(), 
			new File(resampleExportPath, resampleVSETFile.getName()).toPath(),
			StandardCopyOption.REPLACE_EXISTING);
		Files.copy(lightField.viewSet.getGeometryFile().toPath(), 
			new File(resampleExportPath, lightField.viewSet.getGeometryFile().getName()).toPath(),
			StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public void requestFidelity(File exportPath) throws IOException
	{
		this.fidelityRequested = true;
		this.fidelityExportPath = exportPath;
	}
	
	private void generateFidelityDiffs() throws IOException
	{
		System.out.println("\nView Importance:");
		
		Vector3 baseDisplacement = new Vector3(this.lightField.viewSet.getCameraPose(this.lightField.viewSet.getPrimaryViewIndex()).quickInverse(0.001f)
										.times(new Vector4(this.lightField.proxy.getCentroid(), 1.0f)));
		double baseDistanceSquared = baseDisplacement.dot(baseDisplacement);
		
		Program<ContextType> fidelityProgram = context.getShaderProgramBuilder()
				.addShader(ShaderType.VERTEX, new File("shaders/common/texspace_noscale.vert"))
				.addShader(ShaderType.FRAGMENT, new File("shaders/ibr/fidelity.frag"))
				.createProgram();
		
		context.disableBackFaceCulling();
    	
    	Renderable<ContextType> renderable = context.createRenderable(fidelityProgram);
    	renderable.addVertexBuffer("position", this.getLightField().positionBuffer);
    	renderable.addVertexBuffer("texCoord", this.getLightField().texCoordBuffer);
    	renderable.addVertexBuffer("normal", this.getLightField().normalBuffer);
    	renderable.addVertexBuffer("tangent", this.getLightField().tangentBuffer);
    	setupForDraw(fidelityProgram);
		
		for (int i = 0; i < this.lightField.viewSet.getCameraPoseCount(); i++)
		{
			FramebufferObject<ContextType> framebuffer = context.getFramebufferObjectBuilder(1024, 1024)
					.addColorAttachment(ColorFormat.R32F)
					.createFramebufferObject();
	    	
	    	this.setupForDraw();
	    	
	    	renderable.program().setUniform("model_view", this.lightField.viewSet.getCameraPose(i));
	    	renderable.program().setUniform("projection", 
	    			this.lightField.viewSet.getCameraProjection(this.lightField.viewSet.getCameraProjectionIndex(i))
    				.getProjectionMatrix(this.lightField.viewSet.getRecommendedNearPlane(), this.lightField.viewSet.getRecommendedFarPlane()));

	    	renderable.program().setUniform("viewIndex", i);
	    	
	    	if (this.fidelitySetupCallback != null)
    		{
	    		this.fidelitySetupCallback.accept(i);
    		}
	    	
	    	framebuffer.clearColorBuffer(0, -1.0f, -1.0f, -1.0f, -1.0f);
	    	framebuffer.clearDepthBuffer();
	    	
	    	renderable.draw(PrimitiveMode.TRIANGLES, framebuffer);
	    	
	    	File fidelityImage = new File(fidelityExportPath, this.lightField.viewSet.getImageFileName(i));
	        framebuffer.saveColorBufferToFile(0, "PNG", fidelityImage);
	        
	        double sumSqError = 0.0;

	    	float[] fidelityArray = framebuffer.readFloatingPointColorBufferRGBA(0);
	    	for (int k = 0; 4 * k + 3 < fidelityArray.length; k++)
	    	{
    			if (fidelityArray[4 * k] >= 0.0f)
    			{
					sumSqError += fidelityArray[4 * k];
    			}
	    	}

	        
	        Vector3 cameraDisplacement = new Vector3(this.lightField.viewSet.getCameraPose(i)
					.times(new Vector4(this.lightField.proxy.getCentroid(), 1.0f)));
	        
	    	System.out.println(this.lightField.viewSet.getImageFileName(i) + " " + cameraDisplacement.dot(cameraDisplacement) + " " 
	    			+ sumSqError * cameraDisplacement.dot(cameraDisplacement) / baseDistanceSquared);
	        
	        if (this.callback != null)
	        {
	        	this.callback.setProgress((double) i / (double) this.lightField.viewSet.getCameraPoseCount());
	        }
		}
		
		if (this.fidelityCompleteCallback != null)
		{
    		this.fidelityCompleteCallback.run();
		}
		
		fidelityProgram.delete();
	}

	@Override
	public void setHalfResolution(boolean halfResEnabled)
	{
		this.halfResEnabled = halfResEnabled;
	}

	@Override
	public boolean getHalfResolution()
	{
		return this.halfResEnabled;
	}

	@Override
	public void setViewIndexCacheEnabled(boolean viewIndexCacheEnabled)
	{
		this.viewIndexCacheEnabled = viewIndexCacheEnabled;
	}

	@Override
	public boolean isViewIndexCacheEnabled()
	{
		return this.viewIndexCacheEnabled;
	}

	@Override
	public boolean getMultisampling()
	{
		return this.multisamplingEnabled;
	}

	@Override
	public void setMultisampling(boolean multisamplingEnabled)
	{
		this.multisamplingEnabled = multisamplingEnabled;
	}

	public void setClearColor(Vector3 clearColor) 
	{
		this.clearColor = clearColor;
	}

	@Override
	public void setProgram(Program<ContextType> program) 
	{
		this.program = program;
		
		this.mainRenderable = context.createRenderable(program);
    	this.mainRenderable.addVertexBuffer("position", this.lightField.positionBuffer);
    	
    	if (this.lightField.normalBuffer != null)
    	{
    		this.mainRenderable.addVertexBuffer("normal", this.lightField.normalBuffer);
    	}
    	
    	if (this.lightField.texCoordBuffer != null)
    	{
    		this.mainRenderable.addVertexBuffer("texCoord", this.lightField.texCoordBuffer);
    	}
    	
    	if (this.lightField.texCoordBuffer != null)
    	{
    		this.mainRenderable.addVertexBuffer("tangent", this.lightField.tangentBuffer);
    	}
	}
	
	@Override
	public void setIndexProgram(Program<ContextType> program)
	{
		this.viewIndexProgram = program;
		
		this.indexRenderable = context.createRenderable(program);
    	this.indexRenderable.addVertexBuffer("position", this.lightField.positionBuffer);
    	
    	if (this.lightField.normalBuffer != null)
    	{
    		this.indexRenderable.addVertexBuffer("normal", this.lightField.normalBuffer);
    	}
    	
    	if (this.lightField.texCoordBuffer != null)
    	{
    		this.indexRenderable.addVertexBuffer("texCoord", this.lightField.texCoordBuffer);
    	}
	}

	public void setResampleSetupCallback(Consumer<Matrix4> resampleSetupCallback) 
	{
		this.resampleSetupCallback = resampleSetupCallback;
	}

	public void setResampleCompleteCallback(Runnable resampleCompleteCallback) 
	{
		this.resampleCompleteCallback = resampleCompleteCallback;
	}

	public void setFidelitySetupCallback(Consumer<Integer> fidelitySetupCallback) 
	{
		this.fidelitySetupCallback = fidelitySetupCallback;
	}

	public void setFidelityCompleteCallback(Runnable fidelityCompleteCallback) 
	{
		this.fidelityCompleteCallback = fidelityCompleteCallback;
	}

	@Override
	public void requestBTF(int width, int height, File exportPath)
			throws IOException 
	{
		throw new UnsupportedOperationException();
	}
	
	@Override
	public Texture2D<ContextType> getEnvironmentTexture()
	{
		return this.environmentTexture;
	}

	@Override
	public void setEnvironment(File environmentFile) throws IOException
	{
		if (environmentFile != null && environmentFile.exists())
		{
			this.newEnvironmentFile = environmentFile;
		}
	}
	
	public boolean getEnvironmentTextureEnabled()
	{
		return this.environmentTextureEnabled;
	}
	
	public void setEnvironmentTextureEnabled(boolean enabled)
	{
		this.environmentTextureEnabled = enabled;
	}
	
	public Matrix4 getEnvironmentMatrix()
	{
		return this.envMapMatrix;
	}
	
	public void setEnvironmentMatrix(Matrix4 matrix)
	{
		this.envMapMatrix = matrix;
	}
}
