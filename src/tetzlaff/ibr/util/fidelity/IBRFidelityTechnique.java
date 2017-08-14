package tetzlaff.ibr.util.fidelity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import tetzlaff.gl.ColorFormat;
import tetzlaff.gl.Context;
import tetzlaff.gl.Drawable;
import tetzlaff.gl.Framebuffer;
import tetzlaff.gl.PrimitiveMode;
import tetzlaff.gl.Program;
import tetzlaff.gl.ShaderType;
import tetzlaff.gl.UniformBuffer;
import tetzlaff.gl.nativebuffer.NativeDataType;
import tetzlaff.gl.nativebuffer.NativeVectorBuffer;
import tetzlaff.gl.nativebuffer.NativeVectorBufferFactory;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.ibr.IBRSettingsModel;
import tetzlaff.ibr.ReadonlyIBRSettingsModel;
import tetzlaff.ibr.rendering.IBRResources;
import tetzlaff.util.ShadingParameterMode;

public class IBRFidelityTechnique<ContextType extends Context<ContextType>> implements FidelityEvaluationTechnique<ContextType>
{
	private IBRResources<ContextType> resources;
	private Drawable<ContextType> drawable;
	private Framebuffer<ContextType> framebuffer;
    private ReadonlyIBRSettingsModel settings;
    
    private List<Integer> activeViewIndexList;
	
	private Program<ContextType> fidelityProgram;
	private NativeVectorBuffer viewIndexData;
	
	@Override
	public boolean isGuaranteedInterpolating()
	{
		return true;
	}
	
	@Override
	public boolean isGuaranteedMonotonic()
	{
		return false;
	}
	
	@Override
	public double evaluateBaselineError(int targetViewIndex, File debugFile)
	{
		return 0.0;
	}
	
	@Override
	public void initialize(IBRResources<ContextType> resources, ReadonlyIBRSettingsModel settings, int size) throws IOException
	{
		this.resources = resources;
		this.settings = settings;
		
		fidelityProgram = resources.context.getShaderProgramBuilder()
			.addShader(ShaderType.VERTEX, new File("shaders/common/texspace_noscale.vert"))
			.addShader(ShaderType.FRAGMENT, new File("shaders/relight/fidelity.frag"))
			.createProgram();
			
		framebuffer = resources.context.buildFramebufferObject(size, size)
			.addColorAttachment(ColorFormat.RG32F)
			.createFramebufferObject();
		
		resources.context.getState().disableBackFaceCulling();
		
		drawable = resources.context.createDrawable(fidelityProgram);
    	drawable.addVertexBuffer("position", resources.positionBuffer);
    	drawable.addVertexBuffer("texCoord", resources.texCoordBuffer);
    	drawable.addVertexBuffer("normal", resources.normalBuffer);
    	drawable.addVertexBuffer("tangent", resources.tangentBuffer);
    	
    	viewIndexData = NativeVectorBufferFactory.getInstance().createEmpty(NativeDataType.INT, 1, resources.viewSet.getCameraPoseCount());
	}
	
	@Override
	public void setMask(File maskFile) throws IOException
	{
		if (maskFile != null)
		{
			throw new UnsupportedOperationException("Masks are not currently supported.");
		}
	}
	
	@Override
	public void updateActiveViewIndexList(List<Integer> activeViewIndexList) 
	{
		this.activeViewIndexList = new ArrayList<Integer>(activeViewIndexList);
		
		for (int i = 0; i < activeViewIndexList.size(); i++)
		{
			viewIndexData.set(i, 0, activeViewIndexList.get(i));
		}
	}
	
	private NativeVectorBuffer generateViewWeights(IBRResources<?> resources, int targetViewIndex)
	{
		float[] viewWeights = new float[resources.viewSet.getCameraPoseCount()];
		float viewWeightSum = 0.0f;
		
		for (int k = 0; k < activeViewIndexList.size(); k++)
		{
			int viewIndex = activeViewIndexList.get(k).intValue();
			
			Vector3 viewDir = resources.viewSet.getCameraPose(viewIndex).times(resources.geometry.getCentroid().asPosition()).getXYZ().negated().normalized();
			Vector3 targetDir = resources.viewSet.getCameraPose(viewIndex).times(
					resources.viewSet.getCameraPose(targetViewIndex).quickInverse(0.01f).getColumn(3)
						.minus(resources.geometry.getCentroid().asPosition())).getXYZ().normalized();
			
			viewWeights[viewIndex] = 1.0f / (float)Math.max(0.000001, 1.0 - Math.pow(Math.max(0.0, targetDir.dot(viewDir)), this.settings.getWeightExponent())) - 1.0f;
			viewWeightSum += viewWeights[viewIndex];
		}
		
		for (int i = 0; i < viewWeights.length; i++)
		{
			viewWeights[i] /= viewWeightSum;
		}
		
		return NativeVectorBufferFactory.getInstance().createFromFloatArray(1, viewWeights.length, viewWeights);
	}

	@Override
	public double evaluateError(int targetViewIndex, File debugFile) 
	{
		resources.setupShaderProgram(drawable.program(), false);
		
		//NativeVectorBuffer viewWeightBuffer = null;
		UniformBuffer<ContextType> weightBuffer = null;
		
		if (this.settings.getWeightMode() == ShadingParameterMode.UNIFORM)
		{
			drawable.program().setUniform("perPixelWeightsEnabled", false);
			weightBuffer = resources.context.createUniformBuffer().setData(
				/*viewWeightBuffer = */this.generateViewWeights(resources, targetViewIndex));
			drawable.program().setUniformBuffer("ViewWeights", weightBuffer);
			drawable.program().setUniform("occlusionEnabled", false);
		}
		else
		{
			drawable.program().setUniform("perPixelWeightsEnabled", true);
			
			drawable.program().setUniform("weightExponent", this.settings.getWeightExponent());
			drawable.program().setUniform("occlusionEnabled", resources.depthTextures != null && this.settings.isOcclusionEnabled());
			drawable.program().setUniform("occlusionBias", this.settings.getOcclusionBias());
		}
    	
    	drawable.program().setUniform("model_view", resources.viewSet.getCameraPose(targetViewIndex));
    	drawable.program().setUniform("viewPos", resources.viewSet.getCameraPose(targetViewIndex).quickInverse(0.01f).getColumn(3).getXYZ());
    	drawable.program().setUniform("projection", 
    			resources.viewSet.getCameraProjection(resources.viewSet.getCameraProjectionIndex(targetViewIndex))
				.getProjectionMatrix(resources.viewSet.getRecommendedNearPlane(), resources.viewSet.getRecommendedFarPlane()));

    	drawable.program().setUniform("targetViewIndex", targetViewIndex);
		
    	try (UniformBuffer<ContextType> viewIndexBuffer = resources.context.createUniformBuffer().setData(viewIndexData))
    	{
	    	drawable.program().setUniformBuffer("ViewIndices", viewIndexBuffer);
	    	drawable.program().setUniform("viewCount", activeViewIndexList.size());
	    	
	    	framebuffer.clearColorBuffer(0, -1.0f, -1.0f, -1.0f, -1.0f);
	    	framebuffer.clearDepthBuffer();
	    	
	    	drawable.draw(PrimitiveMode.TRIANGLES, framebuffer);
    	}

    	try
    	{
	        if (debugFile != null)
	        {
		    	framebuffer.saveColorBufferToFile(0, "PNG", debugFile);
	        }
    	}
    	catch(IOException e)
    	{
    		e.printStackTrace();
    	}
    	
//    	// Alternate error calculation method that should give the same result in theory
//		MatrixSystem system = getMatrixSystem(targetViewIndex, new AbstractList<Integer>()
//		{
//			@Override
//			public Integer get(int index) 
//			{
//				return viewIndexData.get(index, 0).intValue();
//			}
//
//			@Override
//			public int size() 
//			{
//				return activeViewCount;
//			}
//		},
//		encodedVector -> new Vector3(
//				encodedVector.x / unitReflectanceEncoding,
//				encodedVector.y / unitReflectanceEncoding,
//				encodedVector.z / unitReflectanceEncoding));
		
//		SimpleMatrix weightVector = new SimpleMatrix(activeViewCount, 1);
//		for (int i = 0; i < activeViewCount; i++)
//		{
//			int viewIndex = viewIndexData.get(i, 0).intValue();
//			weightVector.set(i, viewWeightBuffer.get(viewIndex, 0).doubleValue());
//		}
//		
//        SimpleMatrix recon = system.mA.mult(weightVector);
//        SimpleMatrix error = recon.minus(system.b);
//		double matrixError = error.normF() / Math.sqrt(system.b.numRows() / 3);
        
		// Primary error calculation method
        double sumSqError = 0.0;
        //double sumWeights = 0.0;
        double sumMask = 0.0;

    	float[] fidelityArray = framebuffer.readFloatingPointColorBufferRGBA(0);
    	for (int k = 0; 4 * k + 3 < fidelityArray.length; k++)
    	{
			if (fidelityArray[4 * k + 1] >= 0.0f)
			{
				sumSqError += fidelityArray[4 * k];
				//sumWeights += fidelityArray[4 * k + 1];
				sumMask += 1.0;
			}
    	}
    	
    	if (weightBuffer != null)
    	{
    		weightBuffer.close();
    	}

    	double renderError = Math.sqrt(sumSqError / sumMask);
    	return renderError;
	}

	@Override
	public void close() throws Exception 
	{
		// TODO Auto-generated method stub
		
	}
}
