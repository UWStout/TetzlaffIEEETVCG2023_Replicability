/*
 * Copyright (c) Michael Tetzlaff 2019
 * Copyright (c) The Regents of the University of Minnesota 2019
 *
 * Licensed under GPLv3
 * ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 * This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.ibrelight.rendering;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

import org.lwjgl.*;
import tetzlaff.gl.builders.ProgramBuilder;
import tetzlaff.gl.builders.framebuffer.ColorAttachmentSpec;
import tetzlaff.gl.builders.framebuffer.DepthAttachmentSpec;
import tetzlaff.gl.core.*;
import tetzlaff.gl.core.BlendFunction.Weight;
import tetzlaff.gl.core.ColorFormat.DataType;
import tetzlaff.gl.nativebuffer.NativeDataType;
import tetzlaff.gl.nativebuffer.NativeVectorBuffer;
import tetzlaff.gl.nativebuffer.NativeVectorBufferFactory;
import tetzlaff.gl.util.VertexGeometry;
import tetzlaff.gl.vecmath.*;
import tetzlaff.ibrelight.core.IBRRenderable;
import tetzlaff.ibrelight.core.LoadingMonitor;
import tetzlaff.ibrelight.core.StandardRenderingMode;
import tetzlaff.ibrelight.core.ViewSet;
import tetzlaff.ibrelight.rendering.IBRResources.Builder;
import tetzlaff.ibrelight.util.KNNViewWeightGenerator;
import tetzlaff.interactive.InitializationException;
import tetzlaff.models.*;
import tetzlaff.models.impl.DefaultSettingsModel;
import tetzlaff.models.impl.SafeSettingsModelWrapperFactory;
import tetzlaff.util.AbstractImage;
import tetzlaff.util.ArrayBackedImage;
import tetzlaff.util.EnvironmentMap;
import tetzlaff.util.ShadingParameterMode;

public class IBRImplementation<ContextType extends Context<ContextType>> implements IBRRenderable<ContextType>
{
    private final ContextType context;
    private Program<ContextType> program;

    private Program<ContextType> groundPlaneProgram;
    Drawable<ContextType> groundPlaneDrawable;

    private Program<ContextType> shadowProgram;
    private volatile LoadingMonitor loadingMonitor;
    private boolean suppressErrors = false;
    private SafeReadonlySettingsModel settingsModel;
    private StandardRenderingMode lastCompiledRenderingMode = StandardRenderingMode.IMAGE_BASED;

    private final Builder<ContextType> resourceBuilder;
    private IBRResources<ContextType> resources;

    private Texture3D<ContextType> shadowMaps;
    private FramebufferObject<ContextType> shadowFramebuffer;
    private Drawable<ContextType> shadowDrawable;

    private Program<ContextType> lightProgram;
    private VertexBuffer<ContextType> rectangleVertices;
    private Texture2D<ContextType> lightTexture;
    private Texture2D<ContextType> lightCenterTexture;
    private Drawable<ContextType> lightDrawable;

    private Program<ContextType> solidProgram;
    private VertexBuffer<ContextType> widgetVertices;
    private Drawable<ContextType> widgetDrawable;
    private VertexBuffer<ContextType> gridVertices;
    private Drawable<ContextType> gridDrawable;
    
    private final String id;
    private Drawable<ContextType> mainDrawable;

    private ReadonlyObjectModel objectModel;
    private ReadonlyCameraModel cameraModel;
    private ReadonlyLightingModel lightingModel;

    private Vector3 clearColor;
    private Program<ContextType> simpleTexProgram;
    private Drawable<ContextType> simpleTexDrawable;
    private Program<ContextType> tintedTexProgram;
    private Drawable<ContextType> tintedTexDrawable;

    private boolean newEnvironmentDataAvailable;
    private EnvironmentMap newEnvironmentData;
    private boolean environmentMapUnloadRequested = false;
    private Cubemap<ContextType> environmentMap;
    private File currentEnvironmentFile;
    private long environmentLastModified;
    private final Object loadEnvironmentLock = new Object();

    @SuppressWarnings("FieldCanBeLocal")
    private volatile File desiredEnvironmentFile;

    private boolean newBackplateDataAvailable;
    private BufferedImage newBackplateData;
    private boolean backplateUnloadRequested = false;
    private Texture2D<ContextType> backplateTexture;
    private File currentBackplateFile;
    private long backplateLastModified;
    private final Object loadBackplateLock = new Object();

    private boolean newLuminanceEncodingDataAvailable;
    private double[] newLinearLuminanceValues;
    private byte[] newEncodedLuminanceValues;

    private boolean newLightCalibrationAvailable;
    private Vector3 newLightCalibration;

    @SuppressWarnings("FieldCanBeLocal")
    private volatile File desiredBackplateFile;

    private Program<ContextType> environmentBackgroundProgram;
    private Drawable<ContextType> environmentBackgroundDrawable;
    
    private UniformBuffer<ContextType> weightBuffer;

    private FramebufferObject<ContextType> screenSpaceDepthFBO;

    private Vector3 centroid;
    private float boundingRadius;

    
    private final String[] sceneObjectNameList;
    private final Map<String, Integer> sceneObjectIDLookup;
    private IntBuffer pixelObjectIDBuffer;
    private ShortBuffer pixelDepthBuffer;
    private FramebufferSize fboSize;

    private Program<ContextType> circleProgram;
    private Drawable<ContextType> circleDrawable;

    private static final int SHADING_FRAMEBUFFER_COUNT = 2;
    private final Collection<FramebufferObject<ContextType>> shadingFramebuffers = new ArrayList<>(SHADING_FRAMEBUFFER_COUNT);

    IBRImplementation(String id, ContextType context, Program<ContextType> program, Builder<ContextType> resourceBuilder)
    {
        this.id = id;
        this.context = context;
        this.program = program;
        this.resourceBuilder = resourceBuilder;

        this.clearColor = new Vector3(0.0f);
        this.settingsModel = new DefaultSettingsModel();

        this.sceneObjectNameList = new String[256];
        this.sceneObjectIDLookup = new HashMap<>(256);

        this.sceneObjectNameList[0] = null;

        this.sceneObjectNameList[1] = "IBRObject";
        this.sceneObjectIDLookup.put("IBRObject", 1);

        this.sceneObjectNameList[2] = "EnvironmentMap";
        this.sceneObjectIDLookup.put("EnvironmentMap", 2);

        this.sceneObjectNameList[3] = "SceneObject";
        this.sceneObjectIDLookup.put("SceneObject", 3);

        int k = 4;

        for (int i = 0; i < 4; i++)
        {
            this.sceneObjectNameList[k] = "Light." + i;
            this.sceneObjectIDLookup.put("Light." + i, k);
            k++;

            this.sceneObjectNameList[k] = "Light." + i + ".Center";
            this.sceneObjectIDLookup.put("Light." + i + ".Center", k);
            k++;

            this.sceneObjectNameList[k] = "Light." + i + ".Azimuth";
            this.sceneObjectIDLookup.put("Light." + i + ".Azimuth", k);
            k++;

            this.sceneObjectNameList[k] = "Light." + i + ".Inclination";
            this.sceneObjectIDLookup.put("Light." + i + ".Inclination", k);
            k++;

            this.sceneObjectNameList[k] = "Light." + i + ".Distance";
            this.sceneObjectIDLookup.put("Light." + i + ".Distance", k);
            k++;
        }
    }

    @Override
    public IBRResources<ContextType> getResources()
    {
        return this.resources;
    }

    @Override
    public void initialize() throws InitializationException
    {
        try
        {
            this.simpleTexProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "texture.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "common"), "texture.frag"))
                    .createProgram();

            this.tintedTexProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "texture.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "common"), "texture_tint.frag"))
                    .createProgram();

            this.environmentBackgroundProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "texture.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "common"), "envbackgroundtexture.frag"))
                    .createProgram();

            this.rectangleVertices = context.createRectangle();

            this.resources = resourceBuilder.create();
            context.flush();

            if (this.loadingMonitor != null)
            {
                this.loadingMonitor.setMaximum(0.0); // make indeterminate
            }

            if (this.program == null)
            {
                this.program = loadMainProgram();
            }

            if (this.groundPlaneProgram == null)
            {
                this.groundPlaneProgram = loadMainProgram(getReferenceScenePreprocessorDefines(), StandardRenderingMode.LAMBERTIAN_SHADED);
            }

            groundPlaneDrawable = context.createDrawable(groundPlaneProgram);
            groundPlaneDrawable.addVertexBuffer("position", rectangleVertices);
            groundPlaneDrawable.setVertexAttrib("normal", new Vector3(0, 0, 1));

            this.mainDrawable = context.createDrawable(program);
            this.mainDrawable.addVertexBuffer("position", this.resources.positionBuffer);

            if (this.resources.normalBuffer != null)
            {
                this.mainDrawable.addVertexBuffer("normal", this.resources.normalBuffer);
            }

            if (this.resources.texCoordBuffer != null)
            {
                this.mainDrawable.addVertexBuffer("texCoord", this.resources.texCoordBuffer);
            }

            if (this.resources.tangentBuffer != null)
            {
                this.mainDrawable.addVertexBuffer("tangent", this.resources.tangentBuffer);
            }

            this.simpleTexDrawable = context.createDrawable(simpleTexProgram);
            this.simpleTexDrawable.addVertexBuffer("position", this.rectangleVertices);

            this.tintedTexDrawable = context.createDrawable(tintedTexProgram);
            this.tintedTexDrawable.addVertexBuffer("position", this.rectangleVertices);

            this.environmentBackgroundDrawable = context.createDrawable(environmentBackgroundProgram);
            this.environmentBackgroundDrawable.addVertexBuffer("position", this.rectangleVertices);

            this.screenSpaceDepthFBO = context.buildFramebufferObject(512, 512)
                    .addDepthAttachment(DepthAttachmentSpec.createFixedPointWithPrecision(16).setLinearFilteringEnabled(true))
                    .createFramebufferObject();

            shadowProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "depth.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "common"), "depth.frag"))
                    .createProgram();

            shadowDrawable = context.createDrawable(shadowProgram);

            this.solidProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "imgspace.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "common"), "solid.frag"))
                    .createProgram();
            this.widgetVertices = context.createVertexBuffer()
                    .setData(NativeVectorBufferFactory.getInstance()
                        .createFromFloatArray(3, 3, -1, -1, 0, 1, -1, 0, 0, 1, 0));

            this.widgetDrawable = context.createDrawable(this.solidProgram);
            this.widgetDrawable.addVertexBuffer("position", widgetVertices);

            float[] grid = new float[252];
            for (int i = 0; i < 21; i++)
            {
                grid[i * 12] = i * 0.1f - 1.0f;
                grid[i * 12 + 1] = 0;
                grid[i * 12 + 2] = 1;

                grid[i * 12 + 3] = i * 0.1f - 1.0f;
                grid[i * 12 + 4] = 0;
                grid[i * 12 + 5] = -1;

                grid[i * 12 + 6] = 1;
                grid[i * 12 + 7] = 0;
                grid[i * 12 + 8] = i * 0.1f - 1.0f;

                grid[i * 12 + 9] = -1;
                grid[i * 12 + 10] = 0;
                grid[i * 12 + 11] = i * 0.1f - 1.0f;
            }

            this.gridVertices = context.createVertexBuffer()
                .setData(NativeVectorBufferFactory.getInstance()
                    .createFromFloatArray(3, 84, grid));

            this.gridDrawable = context.createDrawable(this.solidProgram);
            this.gridDrawable.addVertexBuffer("position", gridVertices);

            this.lightProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "imgspace.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "relight"), "light.frag"))
                    .createProgram();
            this.lightDrawable = context.createDrawable(this.lightProgram);
            this.lightDrawable.addVertexBuffer("position", rectangleVertices);

            this.circleProgram = context.getShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "imgspace.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "relight"), "circle.frag"))
                    .createProgram();
            this.circleDrawable = context.createDrawable(this.circleProgram);
            this.circleDrawable.addVertexBuffer("position", rectangleVertices);

            NativeVectorBuffer lightTextureData = NativeVectorBufferFactory.getInstance().createEmpty(NativeDataType.FLOAT, 1, 4096);

            NativeVectorBuffer lightCenterTextureData = NativeVectorBufferFactory.getInstance().createEmpty(NativeDataType.FLOAT, 1, 4096);

            int k = 0;
            for (int i = 0; i < 64; i++)
            {
                double x = i * 2.0 / 63.0 - 1.0;

                for (int j = 0; j < 64; j++)
                {
                    double y = j * 2.0 / 63.0 - 1.0;

                    double rSq = x*x + y*y;
                    lightTextureData.set(k, 0, (float)(Math.cos(Math.min(Math.sqrt(rSq), 1.0) * Math.PI) + 1.0) * 0.5f);

                    if (rSq <= 1.0)
                    {
                        lightCenterTextureData.set(k, 0, 1.0f);
                    }

                    k++;
                }
            }

            this.lightTexture = context.getTextureFactory().build2DColorTextureFromBuffer(64, 64, lightTextureData)
                    .setInternalFormat(ColorFormat.R8)
                    .setLinearFilteringEnabled(true)
                    .setMipmapsEnabled(true)
                    .createTexture();

            this.lightCenterTexture = context.getTextureFactory().build2DColorTextureFromBuffer(64, 64, lightCenterTextureData)
                    .setInternalFormat(ColorFormat.R8)
                    .setLinearFilteringEnabled(true)
                    .setMipmapsEnabled(true)
                    .createTexture();

            shadowDrawable.addVertexBuffer("position", resources.positionBuffer);

            shadowMaps = createShadowMaps();
            shadowFramebuffer = context.buildFramebufferObject(2048, 2048)
                .addDepthAttachment()
                .createFramebufferObject();

            this.updateCentroidAndRadius();

            FramebufferSize windowSize = context.getDefaultFramebuffer().getSize();
            FramebufferObject<ContextType> firstShadingFBO =
                context.buildFramebufferObject(windowSize.width, windowSize.height)
                    .addColorAttachment(
                        ColorAttachmentSpec.createWithInternalFormat(ColorFormat.RGB8)
                            .setLinearFilteringEnabled(true))
                    .addDepthAttachment()
                    .createFramebufferObject();

            shadingFramebuffers.add(firstShadingFBO);

            // Shade the entire first frame before announcing that loading is complete.
            Matrix4 projection = this.getProjectionMatrix(windowSize);
            // TODO break this into blocks just in case there's a GPU timeout?
            this.setupForDraw(this.program);
            this.program.setUniform("projection", projection);

            // Render to off-screen buffer
            mainDrawable.draw(PrimitiveMode.TRIANGLES, firstShadingFBO, 0, 0, windowSize.width, windowSize.height);

            // Flush to prevent timeout
            context.flush();

            if (this.loadingMonitor != null)
            {
                this.loadingMonitor.loadingComplete();
            }
        }
        catch (RuntimeException|IOException e)
        {
            e.printStackTrace();
            this.close();
            if (this.loadingMonitor != null)
            {
                this.loadingMonitor.loadingFailed(e);
            }
            throw new InitializationException(e);
        }
    }

    private Texture3D<ContextType> createShadowMaps()
    {
        return context.getTextureFactory().build2DDepthTextureArray(2048, 2048, lightingModel.getLightCount())
            .setInternalPrecision(32)
            .setFloatingPointEnabled(true)
            .createTexture();
    }

    @Override
    public void update()
    {
        updateCompiledSettings();

        this.updateCentroidAndRadius();

        if (this.environmentMapUnloadRequested && this.environmentMap != null)
        {
            this.environmentMap.close();
            this.environmentMap = null;
            this.environmentMapUnloadRequested = false;
        }

        if (this.backplateUnloadRequested && this.backplateTexture != null)
        {
            this.backplateTexture.close();
            this.backplateTexture = null;
            this.backplateUnloadRequested = false;
        }

        if (this.newEnvironmentDataAvailable)
        {
            try
            {
                Cubemap<ContextType> newEnvironmentTexture = null;

                synchronized(loadEnvironmentLock)
                {
                    if (this.newEnvironmentData != null)
                    {
                        EnvironmentMap environmentData = this.newEnvironmentData;
                        this.newEnvironmentData = null;

                        float[][] sides = environmentData.getData();

                        newEnvironmentTexture = context.getTextureFactory().buildColorCubemap(environmentData.getSide())
                            .loadFace(CubemapFace.POSITIVE_X, NativeVectorBufferFactory.getInstance().createFromFloatArray(3,
                                sides[EnvironmentMap.PX].length / 3, sides[EnvironmentMap.PX]))
                            .loadFace(CubemapFace.NEGATIVE_X, NativeVectorBufferFactory.getInstance().createFromFloatArray(3,
                                sides[EnvironmentMap.NX].length / 3, sides[EnvironmentMap.NX]))
                            .loadFace(CubemapFace.POSITIVE_Y, NativeVectorBufferFactory.getInstance().createFromFloatArray(3,
                                sides[EnvironmentMap.PY].length / 3, sides[EnvironmentMap.PY]))
                            .loadFace(CubemapFace.NEGATIVE_Y, NativeVectorBufferFactory.getInstance().createFromFloatArray(3,
                                sides[EnvironmentMap.NY].length / 3, sides[EnvironmentMap.NY]))
                            .loadFace(CubemapFace.POSITIVE_Z, NativeVectorBufferFactory.getInstance().createFromFloatArray(3,
                                sides[EnvironmentMap.PZ].length / 3, sides[EnvironmentMap.PZ]))
                            .loadFace(CubemapFace.NEGATIVE_Z, NativeVectorBufferFactory.getInstance().createFromFloatArray(3,
                                sides[EnvironmentMap.NZ].length / 3, sides[EnvironmentMap.NZ]))
                            .setInternalFormat(ColorFormat.RGB32F)
                            .setMipmapsEnabled(true)
                            .setLinearFilteringEnabled(true)
                            .createTexture();

                        newEnvironmentTexture.setTextureWrap(TextureWrapMode.Repeat, TextureWrapMode.None);
                    }
                }

                if (newEnvironmentTexture != null)
                {
                    if (this.environmentMap != null)
                    {
                        this.environmentMap.close();
                    }

                    this.environmentMap = newEnvironmentTexture;
                }
            }
            catch (RuntimeException e)
            {
                e.printStackTrace();
            }
            finally
            {
                this.newEnvironmentDataAvailable = false;
                this.loadingMonitor.loadingComplete();
            }
        }

        if (this.newBackplateDataAvailable)
        {
            try
            {
                Texture2D<ContextType> newBackplateTexture = null;

                synchronized(loadBackplateLock)
                {
                    if (this.newBackplateData != null)
                    {
                        BufferedImage backplateData = this.newBackplateData;
                        this.newBackplateData = null;

                        newBackplateTexture = context.getTextureFactory().build2DColorTextureFromImage(backplateData, true)
                            .setInternalFormat(CompressionFormat.RGB_PUNCHTHROUGH_ALPHA1_4BPP)
                            .setLinearFilteringEnabled(true)
                            .setMipmapsEnabled(true)
                            .createTexture();
                    }
                }

                if (newBackplateTexture != null)
                {
                    if (this.backplateTexture != null)
                    {
                        this.backplateTexture.close();
                    }

                    this.backplateTexture = newBackplateTexture;
                }
            }
            catch (RuntimeException e)
            {
                e.printStackTrace();
            }
            finally
            {
                this.newBackplateDataAvailable = false;
            }
        }

        if (this.newLuminanceEncodingDataAvailable)
        {
            this.getActiveViewSet().setTonemapping(
                this.getActiveViewSet().getGamma(),
                this.newLinearLuminanceValues,
                this.newEncodedLuminanceValues);

            this.resources.updateLuminanceMap();

            this.newLightCalibrationAvailable = false;
        }

        if (this.newLightCalibrationAvailable)
        {
            for (int i = 0; i < resources.viewSet.getLightCount(); i++)
            {
                this.getActiveViewSet().setLightPosition(i, newLightCalibration);
            }

            this.resources.updateLightData();
            this.newLightCalibrationAvailable = false;
        }
    }

    @Override
    public Optional<Cubemap<ContextType>> getEnvironmentMap()
    {
        return lightingModel.isEnvironmentMappingEnabled() ? Optional.ofNullable(environmentMap) : Optional.empty();
    }

    private void setupForDraw(Program<ContextType> program)
    {
        this.resources.setupShaderProgram(program);

        program.setUniform("weightExponent", this.settingsModel.getFloat("weightExponent"));
        program.setUniform("isotropyFactor", this.settingsModel.getFloat("isotropyFactor"));
        program.setUniform("occlusionBias", this.settingsModel.getFloat("occlusionBias"));

        float gamma = this.settingsModel.getFloat("gamma");
        program.setUniform("renderGamma", gamma);

        program.setTexture("shadowMaps", shadowMaps);

        if (this.environmentMap == null || !lightingModel.isEnvironmentMappingEnabled())
        {
            program.setTexture("environmentMap", context.getTextureFactory().getNullTexture(SamplerType.FLOAT_CUBE_MAP));
        }
        else
        {
            program.setUniform("useEnvironmentMap", true);
            program.setTexture("environmentMap", this.environmentMap);
            program.setUniform("environmentMipMapLevel",
                Math.max(0, Math.min(this.environmentMap.getMipmapLevelCount() - 1,
                    this.lightingModel.getEnvironmentMapFilteringBias()
                        + (float)(0.5 *
                            Math.log(6 * (double)this.environmentMap.getFaceSize() * (double)this.environmentMap.getFaceSize()
                                / (double)resources.viewSet.getCameraPoseCount() )
                            / Math.log(2.0)))));
            program.setUniform("diffuseEnvironmentMipMapLevel", this.environmentMap.getMipmapLevelCount() - 1);

            Matrix4 envMapMatrix = this.getEnvironmentMapMatrix();
            program.setUniform("envMapMatrix", envMapMatrix);
        }

        program.setUniform("ambientColor", lightingModel.getAmbientLightColor());

        float maxLuminance = (float)resources.viewSet.getLuminanceEncoding().decodeFunction.applyAsDouble(255.0);

        this.clearColor = new Vector3(
                (float)Math.pow(lightingModel.getBackgroundColor().x / maxLuminance, 1.0 / gamma),
                (float)Math.pow(lightingModel.getBackgroundColor().y / maxLuminance, 1.0 / gamma),
                (float)Math.pow(lightingModel.getBackgroundColor().z / maxLuminance, 1.0 / gamma));
    }

    private void updateCentroidAndRadius()
    {
        this.boundingRadius = resources.geometry.getBoundingRadius();
        this.centroid = resources.geometry.getCentroid();
    }

    private Matrix4 getDefaultCameraPose()
    {
        return resources.viewSet.getCameraPose(resources.viewSet.getPrimaryViewIndex());
    }


    private float getScale()
    {
        return this.boundingRadius * 2;
//         return getDefaultCameraPose()
//                 .times(resources.geometry.getCentroid().asPosition())
//             .getXYZ().length()
//             * this.boundingRadius / this.resources.geometry.getBoundingRadius();
    }

    private Matrix4 getLightMatrix(int lightIndex)
    {
        return getUnscaledMatrix(
                lightingModel.getLightMatrix(lightIndex)
                    .times(objectModel.getTransformationMatrix()))
            .times(getBaseModelMatrix());
    }

    @Override
    public Matrix4 getEnvironmentMapMatrix()
    {
        return getUnscaledMatrix(
                lightingModel.getEnvironmentMapMatrix()
                    .times(objectModel.getTransformationMatrix()))
            .times(getBaseModelMatrix());
    }

    private Matrix4 getLightProjection(int lightIndex)
    {
        Matrix4 lightMatrix = getLightMatrix(lightIndex);

        Vector4 lightDisplacement = lightMatrix.times(this.centroid.asPosition());
        float lightDist = lightDisplacement.getXYZ().length();
        float lookAtDist = lightDisplacement.getXY().length();

        float radius = (float)
            (getDefaultCameraPose().getUpperLeft3x3()
                .times(new Vector3(this.boundingRadius))
                .length() / Math.sqrt(3));

        float fov;
        float farPlane;
        float nearPlane;

        if (lightingModel.isGroundPlaneEnabled())
        {
            fov = 2.0f * (float)Math.asin(Math.min(0.99, (this.getScale() + lookAtDist) / lightDist));
            farPlane = lightDist + 2 * this.getScale();
            nearPlane = Math.max((lightDist + radius) / 32.0f, lightDist - 2 * radius);
        }
        else
        {
            fov = 2.0f * (float)Math.asin(Math.min(0.99, (radius + lookAtDist) / lightDist));
            farPlane = lightDist + radius;
            nearPlane = Math.max(farPlane / 1024.0f, lightDist - 2 * radius);
        }

        // Limit fov by the light's spot size.
        float spotFOV = 2.0f * lightingModel.getLightPrototype(lightIndex).getSpotSize();
        fov = Math.min(fov, spotFOV);

        return Matrix4.perspective(fov, 1.0f, nearPlane, farPlane);
    }

    private void generateShadowMaps(int lightIndex)
    {
        Matrix4 lightProj = getLightProjection(lightIndex);

        shadowProgram.setUniform("projection", lightProj);

        FramebufferAttachment<ContextType> attachment = shadowMaps.getLayerAsFramebufferAttachment(lightIndex);

        shadowFramebuffer.setDepthAttachment(attachment);
        shadowFramebuffer.clearDepthBuffer();

        shadowProgram.setUniform("model_view", getLightMatrix(lightIndex));
        shadowDrawable.draw(PrimitiveMode.TRIANGLES, shadowFramebuffer);
    }

    private void setupLight(Program<ContextType> program, int lightIndex)
    {
        setupLight(program, lightIndex, getLightMatrix(lightIndex));

        // lightMatrix can be hardcoded here (comment out previous line)

        // Contemporary gallery and stonewall
        //Matrix4.rotateY(16 * Math.PI / 16).times(Matrix4.rotateX(0 * Math.PI / 16))

        // Color studio 2:
        //Matrix4.rotateY(6 * Math.PI / 16).times(Matrix4.rotateX(0 * Math.PI / 16))

        // For the synthetic falcon example?
        //Matrix4.rotateY(5 * Math.PI / 4).times(Matrix4.rotateX(-Math.PI / 4))

        // Always end with this when hardcoding:
        //    .times(new Matrix4(new Matrix3(getDefaultCameraPose())));
    }

    private void setupLight(Program<ContextType> program, int lightIndex, Matrix4 lightMatrix)
    {
        Matrix4 lightMatrixInverse = lightMatrix.quickInverse(0.001f);

        Vector3 lightPos = lightMatrixInverse.times(Vector4.ORIGIN).getXYZ();

        program.setUniform("lightPosVirtual[" + lightIndex + ']', lightPos);

        Vector3 controllerLightIntensity = lightingModel.getLightPrototype(lightIndex).getColor();
        float lightDistance = getLightMatrix(lightIndex).times(this.centroid.asPosition()).getXYZ().length();

        float lightScale = resources.viewSet.areLightSourcesInfinite() ? 1.0f :
                getDefaultCameraPose()
                        .times(resources.geometry.getCentroid().asPosition())
                    .getXYZ().length();

        program.setUniform("lightIntensityVirtual[" + lightIndex + ']',
                controllerLightIntensity.times(lightDistance * lightDistance * resources.viewSet.getLightIntensity(0).y / (lightScale * lightScale)));
        program.setUniform("lightMatrixVirtual[" + lightIndex + ']', getLightProjection(lightIndex).times(lightMatrix));
        program.setUniform("lightOrientationVirtual[" + lightIndex + ']',
            lightMatrixInverse.times(new Vector4(0.0f, 0.0f, -1.0f, 0.0f)).getXYZ().normalized());
        program.setUniform("lightSpotSizeVirtual[" + lightIndex + ']',
            (float)Math.sin(lightingModel.getLightPrototype(lightIndex).getSpotSize()));
        program.setUniform("lightSpotTaperVirtual[" + lightIndex + ']', lightingModel.getLightPrototype(lightIndex).getSpotTaper());
    }

    private Matrix4 getCurrentViewMatrix()
    {
        return getUnscaledMatrix(cameraModel.getLookMatrix());
    }

    private Matrix4 getViewMatrixFromCameraPose(Matrix4 cameraPoseMatrix)
    {
        return cameraPoseMatrix
                .times(Matrix4.translate(this.centroid))
                .times(getDefaultCameraPose().getUpperLeft3x3().asMatrix4().quickInverse(0.01f));
    }

    private Matrix4 getCameraPoseFromViewMatrix(Matrix4 cameraPoseMatrix)
    {
        return cameraPoseMatrix
                .times(getDefaultCameraPose().getUpperLeft3x3().asMatrix4())
                .times(Matrix4.translate(this.centroid.negated()));
    }

    @Override
    public Matrix4 getUnscaledMatrix(Matrix4 scaledMatrix)
    {
        float scale = getScale();

        return Matrix4.scale(scale)
            .times(scaledMatrix)
            .times(Matrix4.scale(1.0f / scale));
    }

    @Override
    public Matrix4 getBaseModelMatrix()
    {
         return getDefaultCameraPose().getUpperLeft3x3().asMatrix4()
            .times(Matrix4.translate(this.centroid.negated()));
    }


    private Matrix4 getModelViewMatrix(Matrix4 view)
    {
        return view
            .times(getUnscaledMatrix(this.objectModel.getTransformationMatrix()))
            .times(getBaseModelMatrix());
    }

    private Matrix4 getViewFromModelViewMatrix(Matrix4 modelViewMatrix)
    {
        return modelViewMatrix
            .times(Matrix4.translate(this.centroid))
            .times(getDefaultCameraPose().getUpperLeft3x3().asMatrix4().quickInverse(0.01f))
            .times(getUnscaledMatrix(this.objectModel.getTransformationMatrix().quickInverse(0.01f)));
    }

    private float getVerticalFieldOfView(FramebufferSize size)
    {
//        return resources.viewSet.getCameraProjection(
//                resources.viewSet.getCameraProjectionIndex(resources.viewSet.getPrimaryViewIndex()))
//            .getVerticalFieldOfView();
        return 2 * (float)Math.atan(Math.tan(cameraModel.getHorizontalFOV() / 2) * size.height / size.width);
    }

    private Matrix4 getProjectionMatrix(FramebufferSize size)
    {
        float scale = getScale();

        return Matrix4.perspective(getVerticalFieldOfView(size),
                (float)size.width / (float)size.height,
                0.01f * scale, 100.0f * scale);
    }

    private NativeVectorBuffer generateViewWeights(Matrix4 targetView)
    {
        float[] viewWeights = //new PowerViewWeightGenerator(settings.getWeightExponent())
            new KNNViewWeightGenerator(4)
                .generateWeights(resources,
                    new AbstractList<Integer>()
                    {
                        @Override
                        public Integer get(int index)
                        {
                            return index;
                        }

                        @Override
                        public int size()
                        {
                            return resources.viewSet.getCameraPoseCount();
                        }
                    },
                    targetView);

        return NativeVectorBufferFactory.getInstance().createFromFloatArray(1, viewWeights.length, viewWeights);
    }

    private float computeLightWidgetScale(Matrix4 viewMatrix, FramebufferSize size)
    {
        float cameraDistance = viewMatrix
            .times(this.cameraModel.getTarget().times(this.getScale()).asPosition())
            .getXYZ().length();
        return cameraDistance * Math.min(cameraModel.getHorizontalFOV(), getVerticalFieldOfView(size)) / 4;
    }

    private void setupModelView(Program<ContextType> p, Matrix4 modelView)
    {
        for (int lightIndex = 0; lightIndex < lightingModel.getLightCount(); lightIndex++)
        {
            setupLight(p, lightIndex);
        }

        p.setUniform("model_view", modelView);
        p.setUniform("viewPos", modelView.quickInverse(0.01f).getColumn(3).getXYZ());

        if (!this.settingsModel.getBoolean("relightingEnabled") && !settingsModel.getBoolean("lightCalibrationMode")
            && this.settingsModel.get("weightMode", ShadingParameterMode.class) == ShadingParameterMode.UNIFORM)
        {
            if (weightBuffer == null)
            {
                weightBuffer = context.createUniformBuffer();
            }
            weightBuffer.setData(this.generateViewWeights(modelView)); // TODO modelView might not be the right matrix?
            p.setUniformBuffer("ViewWeights", weightBuffer);
        }
    }

    private void setupProjection(Program<ContextType> p, int fullWidth, int fullHeight, int x, int y, int width, int height, Matrix4 projection)
    {
        float scaleX = (float)fullWidth / (float)width;
        float scaleY = (float)fullHeight / (float)height;
        float centerX = (2 * x + width - fullWidth) / (float)fullWidth;
        float centerY = (2 * y + height - fullHeight) / (float)fullHeight;

        Matrix4 adjustedProjection = Matrix4.scale(scaleX, scaleY, 1.0f)
            .times(Matrix4.translate(-centerX, -centerY, 0))
            .times(projection);

        p.setUniform("projection", adjustedProjection);
        p.setUniform("fullProjection", projection);
    }

    private void drawGroundPlane(Framebuffer<ContextType> framebuffer, Matrix4 view)
    {
        // Set up camera for ground plane program.
        groundPlaneDrawable.program().setUniform("model_view", view);
        groundPlaneDrawable.program().setUniform("viewPos", view.quickInverse(0.01f).getColumn(3).getXYZ());

        // Disable back face culling since the plane is one-sided.
        context.getState().disableBackFaceCulling();

        // Do first pass at half resolution to off-screen buffer
        groundPlaneDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);

        // Re-enable back face culling
        context.getState().enableBackFaceCulling();
    }

    @Override
    public void draw(Framebuffer<ContextType> framebuffer)
    {
        FramebufferSize framebufferSize = framebuffer.getSize();
        draw(framebuffer, null, null, framebufferSize.width, framebufferSize.height);
    }

    @Override
    public void draw(Framebuffer<ContextType> framebuffer, Matrix4 viewOverride, Matrix4 projectionOverride)
    {
        FramebufferSize framebufferSize = framebuffer.getSize();
        this.draw(framebuffer, viewOverride, projectionOverride, framebufferSize.width, framebufferSize.height);
    }

    @Override
    public void setTonemapping(double[] linearLuminanceValues, byte[] encodedLuminanceValues)
    {
        this.newLinearLuminanceValues = linearLuminanceValues;
        this.newEncodedLuminanceValues = encodedLuminanceValues;
        this.newLuminanceEncodingDataAvailable = true;
    }

    @Override
    public void applyLightCalibration()
    {
        this.newLightCalibration = resources.viewSet.getLightPosition(resources.viewSet.getLightIndex(resources.viewSet.getPrimaryViewIndex()))
            .plus(settingsModel.get("currentLightCalibration", Vector2.class).asVector3());
        this.newLightCalibrationAvailable = true;
    }


    @Override
    public void draw(Framebuffer<ContextType> framebuffer, Matrix4 modelViewOverride, Matrix4 projectionOverride, int subdivWidth, int subdivHeight)
    {
        boolean overriddenViewMatrix = modelViewOverride != null;

        //Matrix4 view = this.getAbsoluteViewMatrix();
        Matrix4 view = this.getCurrentViewMatrix();

        try
        {
            if(this.settingsModel.getBoolean("multisamplingEnabled"))
            {
                context.getState().enableMultisampling();
            }
            else
            {
                context.getState().disableMultisampling();
            }

            context.getState().enableBackFaceCulling();

            boolean lightCalibrationMode = false;
            int snapViewIndex = -1;

            if (overriddenViewMatrix)
            {
                view = getViewFromModelViewMatrix(modelViewOverride);
            }
            else if (settingsModel.getBoolean("lightCalibrationMode"))
            {
                lightCalibrationMode = true;

                int primaryLightIndex = this.resources.viewSet.getLightIndex(this.resources.viewSet.getPrimaryViewIndex());

                Vector3 lightPosition = settingsModel.get("currentLightCalibration", Vector2.class).asVector3()
                                            .plus(resources.viewSet.getLightPosition(primaryLightIndex));
                Matrix4 lightTransform = Matrix4.translate(lightPosition.negated());

                Matrix4 viewInverse = getCurrentViewMatrix().quickInverse(0.01f);
                float maxSimilarity = -1.0f;

                for(int i = 0; i < this.resources.viewSet.getCameraPoseCount(); i++)
                {
                    Matrix4 candidateView = this.resources.viewSet.getCameraPose(i);

                    float similarity = viewInverse.times(Vector4.ORIGIN).getXYZ()
                        .dot(getViewMatrixFromCameraPose(candidateView).quickInverse(0.01f).times(Vector4.ORIGIN).getXYZ());

                    if (similarity > maxSimilarity)
                    {
                        maxSimilarity = similarity;
                        view = lightTransform.times(getViewMatrixFromCameraPose(candidateView));
                        snapViewIndex = i;
                    }
                }
            }

            FramebufferSize size = framebuffer.getSize();

            Matrix4 projection = projectionOverride;

            if (projection == null)
            {
                projection = this.getProjectionMatrix(size);
            }

            int fboWidth = size.width;
            int fboHeight = size.height;

            if (settingsModel.getBoolean("halfResolutionEnabled"))
            {
                fboWidth /= 2;
                fboHeight /= 2;
            }

            try
            (
                FramebufferObject<ContextType> offscreenFBO = context.buildFramebufferObject(fboWidth, fboHeight)
                        .addColorAttachment(ColorAttachmentSpec.createWithInternalFormat(ColorFormat.RGB8)
                            .setLinearFilteringEnabled(true))
                        .addColorAttachment(ColorAttachmentSpec.createWithInternalFormat(ColorFormat.R8UI))
                        .addDepthAttachment(DepthAttachmentSpec.createFixedPointWithPrecision(24))
                        .createFramebufferObject();

                UniformBuffer<ContextType> viewIndexBuffer = context.createUniformBuffer()
            )
            {
                offscreenFBO.clearIntegerColorBuffer(1, 0, 0, 0, 0);
                offscreenFBO.clearDepthBuffer();

                if (backplateTexture != null && lightingModel.getBackgroundMode() == BackgroundMode.IMAGE)
                {
                    tintedTexDrawable.program().setTexture("tex", backplateTexture);
                    tintedTexDrawable.program().setUniform("color", clearColor);

                    context.getState().disableDepthTest();
                    tintedTexDrawable.draw(PrimitiveMode.TRIANGLE_FAN, offscreenFBO);
                    context.getState().enableDepthTest();

                    // Clear ID buffer again.
                    offscreenFBO.clearIntegerColorBuffer(1, 0, 0, 0, 0);
                }
                else if (environmentMap != null && lightingModel.getBackgroundMode() == BackgroundMode.ENVIRONMENT_MAP)
                {
                    float scale = getScale();
                    Matrix4 envMapMatrix = lightingModel.getEnvironmentMapMatrix();

                    environmentBackgroundProgram.setUniform("objectID", this.sceneObjectIDLookup.get("EnvironmentMap"));
                    environmentBackgroundProgram.setUniform("useEnvironmentTexture", true);
                    environmentBackgroundProgram.setTexture("env", environmentMap);
                    environmentBackgroundProgram.setUniform("model_view", view);
                    environmentBackgroundProgram.setUniform("projection", projection);
                    environmentBackgroundProgram.setUniform("envMapMatrix", envMapMatrix);
                    environmentBackgroundProgram.setUniform("envMapIntensity", this.clearColor);

                    environmentBackgroundProgram.setUniform("gamma",
                        environmentMap.isInternalFormatCompressed() ||
                            environmentMap.getInternalUncompressedColorFormat().dataType != DataType.FLOATING_POINT
                            ? 1.0f : 2.2f);

                    context.getState().disableDepthTest();
                    environmentBackgroundDrawable.draw(PrimitiveMode.TRIANGLE_FAN, offscreenFBO);
                    context.getState().enableDepthTest();
                }
                else
                {
                    offscreenFBO.clearColorBuffer(0, clearColor.x, clearColor.y, clearColor.z, 1.0f);
                }

                // Too many lights; need to re-allocate shadow maps
                if (shadowMaps.getDepth() < lightingModel.getLightCount())
                {
                    shadowMaps.close();
                    shadowMaps = null;
                    shadowMaps = createShadowMaps();
                }

                for (int lightIndex = 0; lightIndex < lightingModel.getLightCount(); lightIndex++)
                {
                    generateShadowMaps(lightIndex);
                }

                // Draw grid
                if (settingsModel.getBoolean("is3DGridEnabled"))
                {
                    this.solidProgram.setUniform("projection", this.getProjectionMatrix(size));
                    this.solidProgram.setUniform("model_view", view.times(Matrix4.scale(this.getScale())));
                    this.solidProgram.setUniform("color", new Vector4(0.5f, 0.5f, 0.5f, 1.0f));
                    this.solidProgram.setUniform("objectID", 0);
                    this.gridDrawable.draw(PrimitiveMode.LINES, offscreenFBO);
                }

                if (lightingModel.isGroundPlaneEnabled())
                {
                    this.groundPlaneProgram.setUniform("objectID", this.sceneObjectIDLookup.get("SceneObject"));
                    this.groundPlaneProgram.setUniform("defaultDiffuseColor", lightingModel.getGroundPlaneColor());
                    this.groundPlaneProgram.setUniform("projection", this.getProjectionMatrix(size));

                    this.setupForDraw(groundPlaneProgram);

                    float scale = getScale();
                    for (int lightIndex = 0; lightIndex < lightingModel.getLightCount(); lightIndex++)
                    {
                        setupLight(groundPlaneProgram, lightIndex,
                             Matrix4.scale(scale)
                                 .times(lightingModel.getLightMatrix(lightIndex))
                                 .times(Matrix4.translate(new Vector3(0, lightingModel.getGroundPlaneHeight(), 0)))
                                 .times(Matrix4.scale(1.0f / scale))
                                 .times(Matrix4.rotateX(Math.PI / 2))
                                 .times(Matrix4.scale(this.getScale() * lightingModel.getGroundPlaneSize())));
                    }

                    this.drawGroundPlane(offscreenFBO,
                        view
                            .times(Matrix4.scale(scale))
                            .times(Matrix4.translate(new Vector3(0, lightingModel.getGroundPlaneHeight(), 0)))
                            .times(Matrix4.scale(1.0f / scale))
                            .times(Matrix4.rotateX(Math.PI / 2))
                            .times(Matrix4.scale(this.getScale() * lightingModel.getGroundPlaneSize())));
                }

                // After the ground plane, use a gray color for anything without a texture map.
                this.program.setUniform("defaultDiffuseColor", new Vector3(0.125f));

                context.getState().disableBackFaceCulling();

                setupForDraw(this.program);

                this.program.setUniform("objectID", this.sceneObjectIDLookup.get("IBRObject"));

                if (lightCalibrationMode)
                {
                    this.program.setUniform("holeFillColor", new Vector3(0.5f));
                    viewIndexBuffer.setData(NativeVectorBufferFactory.getInstance().createFromIntArray(false, 1, 1, snapViewIndex));
                    this.program.setUniformBuffer("ViewIndices", viewIndexBuffer);
                }
                else
                {
                    this.program.setUniform("holeFillColor", new Vector3(0.0f));
                }

                shadowProgram.setUniform("projection", projection);
                screenSpaceDepthFBO.clearDepthBuffer();

                setupModelView(shadowProgram, view);
                shadowDrawable.draw(PrimitiveMode.TRIANGLES, screenSpaceDepthFBO);

                context.flush();

                this.program.setTexture("screenSpaceDepthBuffer", screenSpaceDepthFBO.getDepthAttachmentTexture());

                Matrix4 modelView = lightCalibrationMode ? getCameraPoseFromViewMatrix(view) : getModelViewMatrix(view);
                drawModelInSubdivisions(this.mainDrawable, offscreenFBO, subdivWidth, subdivHeight,
                        // Don't use the model matrix when in light calibration mode.
                        modelView, projection);

                context.getState().enableBackFaceCulling();

                if (!lightingModel.areLightWidgetsEthereal()
                    && IntStream.range(0, lightingModel.getLightCount()).anyMatch(lightingModel::isLightWidgetEnabled))
                {
                    context.flush();

                    // Read buffers here if light widgets are ethereal (i.e. they cannot be clicked and should not be in the ID buffer)
                    fboSize = offscreenFBO.getSize();

                    if (pixelObjectIDBuffer == null || pixelObjectIDBuffer.capacity() != 4 * fboSize.width * fboSize.height)
                    {
                        pixelObjectIDBuffer = BufferUtils.createIntBuffer(4 * fboSize.width * fboSize.height);
                    }
                    else
                    {
                        pixelObjectIDBuffer.clear();
                    }

                    if (pixelDepthBuffer == null || pixelDepthBuffer.capacity() != fboSize.width * fboSize.height)
                    {
                        pixelDepthBuffer = BufferUtils.createShortBuffer(fboSize.width * fboSize.height);
                    }
                    else
                    {
                        pixelDepthBuffer.clear();
                    }

                    offscreenFBO.readIntegerColorBufferRGBA(1, pixelObjectIDBuffer);
                    offscreenFBO.readDepthBuffer(pixelDepthBuffer);
                }

                drawLights(offscreenFBO, view);

                // Finish drawing
                context.flush();

                // Second pass at full resolution to default framebuffer
                simpleTexDrawable.program().setTexture("tex", offscreenFBO.getColorAttachmentTexture(0));

                framebuffer.clearDepthBuffer();
                simpleTexDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);

                context.flush();

                if (!lightingModel.areLightWidgetsEthereal()
                    && IntStream.range(0, lightingModel.getLightCount()).anyMatch(lightingModel::isLightWidgetEnabled))
                {
                    // Read buffers here if light widgets are not ethereal (i.e. they can be clicked and should be in the ID buffer)
                    fboSize = offscreenFBO.getSize();

                    if (pixelObjectIDBuffer == null || pixelObjectIDBuffer.capacity() != 4 * fboSize.width * fboSize.height)
                    {
                        pixelObjectIDBuffer = BufferUtils.createIntBuffer(4 * fboSize.width * fboSize.height);
                    }
                    else
                    {
                        pixelObjectIDBuffer.clear();
                    }

                    if (pixelDepthBuffer == null || pixelDepthBuffer.capacity() != fboSize.width * fboSize.height)
                    {
                        pixelDepthBuffer = BufferUtils.createShortBuffer(fboSize.width * fboSize.height);
                    }
                    else
                    {
                        pixelDepthBuffer.clear();
                    }

                    offscreenFBO.readIntegerColorBufferRGBA(1, pixelObjectIDBuffer);
                    offscreenFBO.readDepthBuffer(pixelDepthBuffer);
                }
            }
        }
        catch(RuntimeException e)
        {
            if (!suppressErrors)
            {
                e.printStackTrace();
                suppressErrors = true; // Prevent excessive errors
            }
        }
    }

    private void drawModelInSubdivisions(Drawable<ContextType> drawable, Framebuffer<ContextType> framebuffer,
                                         int subdivWidth, int subdivHeight, Matrix4 view, Matrix4 projection)
    {
        FramebufferSize fullFBOSize = framebuffer.getSize();

        // Optionally render in subdivisions to prevent GPU timeout
        for (int x = 0; x < fullFBOSize.width; x += subdivWidth)
        {
            for (int y = 0; y < fullFBOSize.height; y += subdivHeight)
            {
                int effectiveWidth = Math.min(subdivWidth, fullFBOSize.width - x);
                int effectiveHeight = Math.min(subdivHeight, fullFBOSize.height - y);

                setupModelView(drawable.program(), view);
                setupProjection(drawable.program(), fullFBOSize.width, fullFBOSize.height, x, y, effectiveWidth, effectiveHeight, projection);

                // Render to off-screen buffer
                drawable.draw(PrimitiveMode.TRIANGLES, framebuffer, x, y, effectiveWidth, effectiveHeight);

                // Flush to prevent timeout
                context.flush();
            }
        }
    }

    private void drawLights(Framebuffer<ContextType> framebuffer, Matrix4 view)
    {
        FramebufferSize size = framebuffer.getSize();

        if (this.settingsModel.getBoolean("relightingEnabled") && this.settingsModel.getBoolean("visibleLightsEnabled")
            && !settingsModel.getBoolean("lightCalibrationMode"))
        {
            this.context.getState().disableDepthWrite();

            // Draw lights
            for (int i = 0; i < lightingModel.getLightCount(); i++)
            {
                this.context.getState().setBlendFunction(new BlendFunction(Weight.ONE, Weight.ONE));
                this.context.getState().enableDepthTest();

                if (settingsModel.getBoolean("lightWidgetsEnabled") && lightingModel.isLightWidgetEnabled(i)
                    && lightingModel.getLightWidgetModel(i).isCenterWidgetVisible())
                {
                    this.lightProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i + ".Center"));

                    Vector3 lightCenter = view.times(this.lightingModel.getLightCenter(i).times(this.getScale()).asPosition()).getXYZ();

                    this.lightProgram.setUniform("model_view",
                        Matrix4.translate(lightCenter)
                            .times(Matrix4.scale(
                                -lightCenter.z * getVerticalFieldOfView(size) / 64.0f,
                                -lightCenter.z * getVerticalFieldOfView(size) / 64.0f,
                                1.0f)));
                    this.lightProgram.setUniform("projection", this.getProjectionMatrix(size));

                    this.lightProgram.setTexture("lightTexture", this.lightCenterTexture);

                    this.context.getState().disableDepthTest();
                    this.lightProgram.setUniform("color",
                        new Vector3(this.lightingModel.getLightWidgetModel(i).isCenterWidgetSelected() ? 1.0f : 0.5f));
                    this.lightDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                }

                Matrix4 widgetTransformation = view
                    .times(getUnscaledMatrix(lightingModel.getLightMatrix(i).quickInverse(0.01f)));

                if (lightingModel.isLightVisualizationEnabled(i))
                {
                    this.context.getState().setBlendFunction(new BlendFunction(Weight.ONE, Weight.ONE));
                    this.lightProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i));
                    this.lightProgram.setUniform("color", lightingModel.getLightPrototype(i).getColor().times((float)Math.PI));

                    Vector3 lightPosition = widgetTransformation.getColumn(3).getXYZ();

                    this.lightProgram.setUniform("model_view",
                        Matrix4.translate(lightPosition)
                            .times(Matrix4.scale(-lightPosition.z / 32.0f, -lightPosition.z / 32.0f, 1.0f)));
                    this.lightProgram.setUniform("projection", this.getProjectionMatrix(size));
                    this.lightProgram.setTexture("lightTexture", this.lightTexture);
                    this.lightDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                }

                if (settingsModel.getBoolean("lightWidgetsEnabled") && lightingModel.isLightWidgetEnabled(i))
                {
                    this.solidProgram.setUniform("projection", this.getProjectionMatrix(size));

                    float lightWidgetScale = computeLightWidgetScale(view, size);
                    Vector3 lightCenter = view.times(this.lightingModel.getLightCenter(i).times(this.getScale()).asPosition()).getXYZ();
                    Vector3 widgetPosition = widgetTransformation.getColumn(3).getXYZ()
                        .minus(lightCenter)
                        .normalized()
                        .times(lightWidgetScale)
                        .plus(lightCenter);
                    Vector3 widgetDisplacement = widgetPosition.minus(lightCenter);
                    float widgetDistance = widgetDisplacement.length();

                    Vector3 distanceWidgetPosition = widgetTransformation.getColumn(3).getXYZ()
                        .minus(lightCenter)
                        .times(Math.min(1, computeLightWidgetScale(view, size) /
                            widgetTransformation.getColumn(3).getXYZ().distance(lightCenter)))
                        .plus(lightCenter);

                    float perspectiveWidgetScale = -widgetPosition.z * getVerticalFieldOfView(size) / 128;

                    this.context.getState().disableDepthTest();
                    this.context.getState().setBlendFunction(new BlendFunction(Weight.ONE, Weight.ONE));

                    if (lightingModel.getLightWidgetModel(i).isDistanceWidgetVisible() || lightingModel.getLightWidgetModel(i).isCenterWidgetVisible())
                    {
                        Vector3 lineEndpoint = widgetPosition.minus(lightCenter)
                            .times(0.5f / widgetPosition.getXY().distance(lightCenter.getXY()))
                            .minus(lightCenter);

                        try
                            (
                                VertexBuffer<ContextType> line =
                                    context.createVertexBuffer()
                                        .setData(NativeVectorBufferFactory.getInstance()
                                            .createFromFloatArray(3, 2, lineEndpoint.x, lineEndpoint.y, lineEndpoint.z, lightCenter.x, lightCenter.y, lightCenter.z))
                            )
                        {
                            Drawable<ContextType> lineRenderable = context.createDrawable(this.solidProgram);
                            lineRenderable.addVertexBuffer("position", line);
                            this.solidProgram.setUniform("model_view", Matrix4.IDENTITY);
                            this.solidProgram.setUniform("color",
                                new Vector3(lightingModel.getLightWidgetModel(i).isDistanceWidgetSelected()
                                    || lightingModel.getLightWidgetModel(i).isCenterWidgetSelected() ? 1.0f : 0.5f)
                                    .asVector4(1));
                            this.solidProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i + ".Distance"));
                            lineRenderable.draw(PrimitiveMode.LINES, framebuffer);
                        }
                    }

                    if (lightingModel.getLightWidgetModel(i).isInclinationWidgetVisible()
                        && lightingModel.getLightWidgetModel(i).isInclinationWidgetSelected())
                    {
                        Vector3 lineEndpoint1 = lightCenter
                            .plus(view.times(new Vector4(0,widgetDistance,0,0)).getXYZ());
                        Vector3 lineEndpoint2 = lightCenter
                            .plus(view.times(new Vector4(0,-widgetDistance,0,0)).getXYZ());

                        try
                            (
                                VertexBuffer<ContextType> line =
                                    context.createVertexBuffer()
                                        .setData(NativeVectorBufferFactory.getInstance()
                                            .createFromFloatArray(3, 2, lineEndpoint1.x, lineEndpoint1.y, lineEndpoint1.z, lineEndpoint2.x, lineEndpoint2.y, lineEndpoint2.z))
                            )
                        {
                            Drawable<ContextType> lineRenderable = context.createDrawable(this.solidProgram);
                            lineRenderable.addVertexBuffer("position", line);
                            this.solidProgram.setUniform("model_view", Matrix4.IDENTITY);
                            this.solidProgram.setUniform("color",
                                new Vector3(lightingModel.getLightWidgetModel(i).isDistanceWidgetSelected()
                                    || lightingModel.getLightWidgetModel(i).isCenterWidgetSelected() ? 1.0f : 0.5f)
                                    .asVector4(1));
                            this.solidProgram.setUniform("objectID", 0);
                            lineRenderable.draw(PrimitiveMode.LINES, framebuffer);
                        }
                    }

                    Vector3 azimuthRotationAxis = view.times(new Vector4(0,1,0,0)).getXYZ();

                    this.circleProgram.setUniform("color", new Vector3(1));
                    this.circleProgram.setUniform("projection", this.getProjectionMatrix(size));
                    this.circleProgram.setUniform("width", 1 / 128.0f);
                    this.circleProgram.setUniform("maxAngle", (float)Math.PI / 4);
                    this.circleProgram.setUniform("threshold", 0.005f);

                    Vector3 lightDisplacementAtInclination = widgetDisplacement
                        .minus(azimuthRotationAxis.times(widgetDisplacement.dot(azimuthRotationAxis)));
                    float lightDistanceAtInclination = lightDisplacementAtInclination.length();

                    context.getState().disableBackFaceCulling();

                    Vector3 lightDisplacementWorld = view.quickInverse(0.01f)
                        .times(widgetDisplacement.asDirection()).getXYZ();

                    double azimuth = Math.atan2(lightDisplacementWorld.x, lightDisplacementWorld.z);
                    double inclination = Math.asin(lightDisplacementWorld.normalized().y);

                    float cosineLightToPole = widgetDisplacement.normalized().dot(azimuthRotationAxis);
                    double azimuthArrowRotation = Math.min(Math.PI / 4,
                        16 * perspectiveWidgetScale / (widgetDistance * Math.sqrt(1 - cosineLightToPole * cosineLightToPole)));

                    double inclinationArrowRotation = Math.min(Math.PI / 4, 16 * perspectiveWidgetScale / widgetDistance);

                    if (lightingModel.getLightWidgetModel(i).isAzimuthWidgetVisible() &&
                        (Math.abs(lightDisplacementWorld.x) > 0.001f || Math.abs(lightDisplacementWorld.z) > 0.001f))
                    {
                        // Azimuth circle
                        this.circleProgram.setUniform("maxAngle",
                            (float) (lightingModel.getLightWidgetModel(i).isAzimuthWidgetSelected() ?
                                Math.PI : azimuthArrowRotation));
                        this.circleProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i + ".Azimuth"));
                        this.circleProgram.setUniform("color",
                            new Vector3(lightingModel.getLightWidgetModel(i).isAzimuthWidgetSelected() ? 1.0f :0.5f));
                        this.circleProgram.setUniform("model_view",
                            Matrix4.translate(lightCenter.plus(azimuthRotationAxis.times(widgetDisplacement.dot(azimuthRotationAxis))))
                                .times(view.getUpperLeft3x3().asMatrix4())
                                .times(Matrix4.scale(2 * lightDistanceAtInclination))
                                .times(Matrix4.rotateX(-Math.PI / 2))
                                .times(Matrix4.rotateZ(azimuth - Math.PI / 2)));
                        this.circleDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                    }

                    if (lightingModel.getLightWidgetModel(i).isInclinationWidgetVisible())
                    {
                        // Inclination circle
                        this.circleProgram.setUniform("maxAngle",
                            (float) (lightingModel.getLightWidgetModel(i).isInclinationWidgetSelected() ?
                                Math.PI / 2 : inclinationArrowRotation));
                        this.circleProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i + ".Inclination"));
                        this.circleProgram.setUniform("color",
                            new Vector3(lightingModel.getLightWidgetModel(i).isInclinationWidgetSelected() ? 1.0f : 0.5f));
                        this.circleProgram.setUniform("model_view",
                            Matrix4.translate(lightCenter)
                                .times(Matrix4.scale(2 * widgetDistance))
                                .times(widgetTransformation.getUpperLeft3x3()
                                    .times(Matrix3.rotateY(-Math.PI / 2))
                                    .times(lightingModel.getLightWidgetModel(i).isInclinationWidgetSelected() ?
                                        Matrix3.rotateZ(-inclination) : Matrix3.IDENTITY)
                                    .asMatrix4()));
                        this.circleDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                    }

                    context.getState().enableBackFaceCulling();

                    Vector3 arrow1PositionR = Matrix3.rotateAxis(azimuthRotationAxis, azimuthArrowRotation)
                        .times(widgetPosition.minus(lightCenter))
                        .plus(lightCenter);

                    Vector3 arrow1PositionL = Matrix3.rotateAxis(azimuthRotationAxis, -azimuthArrowRotation)
                        .times(widgetPosition.minus(lightCenter))
                        .plus(lightCenter);

                    Vector3 arrow2PositionR = widgetTransformation.getUpperLeft3x3()
                        .times(Matrix3.rotateX(-inclinationArrowRotation))
                        .times(widgetTransformation.quickInverse(0.01f).getUpperLeft3x3())
                        .times(widgetPosition.minus(lightCenter))
                        .plus(lightCenter);

                    Vector3 arrow2PositionL = widgetTransformation.getUpperLeft3x3()
                        .times(Matrix3.rotateX(inclinationArrowRotation))
                        .times(widgetTransformation.quickInverse(0.01f).getUpperLeft3x3())
                        .times(widgetPosition.minus(lightCenter))
                        .plus(lightCenter);

                    Vector4 arrow1RDirectionY =  Matrix3.rotateAxis(azimuthRotationAxis, azimuthArrowRotation)
                        .times(widgetTransformation.getUpperLeft3x3())
                        .times(new Vector3(1,0,0))
                        .getXY().normalized().asDirection();

                    Vector4 arrow1LDirectionY =  Matrix3.rotateAxis(azimuthRotationAxis, -azimuthArrowRotation)
                        .times(widgetTransformation.getUpperLeft3x3())
                        .times(new Vector3(1,0,0))
                        .getXY().normalized().asDirection();

                    Vector4 arrow2RDirectionY = widgetTransformation.getUpperLeft3x3()
                        .times(Matrix3.rotateX(-inclinationArrowRotation))
                        .times(new Vector3(0,1,0))
                        .getXY().normalized().asDirection();

                    Vector4 arrow2LDirectionY = widgetTransformation.getUpperLeft3x3()
                        .times(Matrix3.rotateX(inclinationArrowRotation))
                        .times(new Vector3(0,1,0))
                        .getXY().normalized().asDirection();

                    // TODO account for perspective distortion in arrow orientation
                    @SuppressWarnings("SuspiciousNameCombination")
                    Vector4 arrow1RDirectionX = new Vector4(arrow1RDirectionY.y, -arrow1RDirectionY.x, 0, 0).normalized();
                    @SuppressWarnings("SuspiciousNameCombination")
                    Vector4 arrow1LDirectionX = new Vector4(arrow1LDirectionY.y, -arrow1LDirectionY.x, 0, 0).normalized();
                    @SuppressWarnings("SuspiciousNameCombination")
                    Vector4 arrow2RDirectionX = new Vector4(arrow2RDirectionY.y, -arrow2RDirectionY.x, 0, 0).normalized();
                    @SuppressWarnings("SuspiciousNameCombination")
                    Vector4 arrow2LDirectionX = new Vector4(arrow2LDirectionY.y, -arrow2LDirectionY.x, 0, 0).normalized();


                    Vector4 arrow3DirectionY = lightCenter.minus(widgetPosition).getXY().normalized().asDirection();
                    @SuppressWarnings("SuspiciousNameCombination")
                    Vector4 arrow3DirectionX = new Vector4(arrow3DirectionY.y, -arrow3DirectionY.x, 0, 0).normalized();

                    Vector3 arrow3PositionR = distanceWidgetPosition.minus(widgetDisplacement.times(0.5f));
                    Vector3 arrow3PositionL = distanceWidgetPosition.plus(widgetDisplacement.times(0.5f));

//                            Vector3 arrow1PositionR = widgetTransformation.times(new Vector4(1,0,0,1)).getXYZ();
//                            Vector3 arrow1PositionL = widgetTransformation.times(new Vector4(-1,0,0,1)).getXYZ();
//                            Vector3 arrow2PositionR = widgetTransformation.times(new Vector4(0,1,0,1)).getXYZ();
//                            Vector3 arrow2PositionL = widgetTransformation.times(new Vector4(0,-1,0,1)).getXYZ();
//                            Vector3 arrow3PositionR = widgetTransformation.times(new Vector4(0,0,1,1)).getXYZ();
//                            Vector3 arrow3PositionL = widgetTransformation.times(new Vector4(0,0,-1,1)).getXYZ();

                    this.context.getState().disableDepthTest();
                    this.context.getState().disableBlending();
                    this.solidProgram.setUniform("color", new Vector4(1));

                    if (lightingModel.getLightWidgetModel(i).isAzimuthWidgetVisible() &&
                        (Math.abs(lightDisplacementWorld.x) > 0.001f || Math.abs(lightDisplacementWorld.z) > 0.001f))
                    {
                        this.solidProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i + ".Azimuth"));

                        this.solidProgram.setUniform("model_view",
                            Matrix4.translate(arrow1PositionR)
                                .times(Matrix4.scale(perspectiveWidgetScale, perspectiveWidgetScale, 1.0f))
                                .times(Matrix4.fromColumns(
                                    arrow1RDirectionX,
                                    arrow1RDirectionY,
                                    new Vector4(0, 0, 1, 0),
                                    new Vector4(0, 0, 0, 1))));

                        this.widgetDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);

                        this.solidProgram.setUniform("model_view",
                            Matrix4.translate(arrow1PositionL)
                                .times(Matrix4.scale(perspectiveWidgetScale, perspectiveWidgetScale, 1.0f))
                                .times(Matrix4.fromColumns(
                                    arrow1LDirectionX.negated(),
                                    arrow1LDirectionY.negated(),
                                    new Vector4(0, 0, 1, 0),
                                    new Vector4(0, 0, 0, 1))));

                        this.widgetDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                    }

                    if (lightingModel.getLightWidgetModel(i).isInclinationWidgetVisible())
                    {
                        this.solidProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i + ".Inclination"));

                        if (Math.PI / 2 - inclination > 0.01f)
                        {
                            this.solidProgram.setUniform("model_view",
                                Matrix4.translate(arrow2PositionR)
                                    .times(Matrix4.scale(perspectiveWidgetScale, perspectiveWidgetScale, 1.0f))
                                    .times(Matrix4.fromColumns(
                                        arrow2RDirectionX,
                                        arrow2RDirectionY,
                                        new Vector4(0, 0, 1, 0),
                                        new Vector4(0, 0, 0, 1))));
                            this.widgetDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                        }

                        if (Math.PI / 2 + inclination > 0.01f)
                        {
                            this.solidProgram.setUniform("model_view",
                                Matrix4.translate(arrow2PositionL)
                                    .times(Matrix4.scale(perspectiveWidgetScale, perspectiveWidgetScale, 1.0f))
                                    .times(Matrix4.fromColumns(
                                        arrow2LDirectionX.negated(),
                                        arrow2LDirectionY.negated(),
                                        new Vector4(0, 0, 1, 0),
                                        new Vector4(0, 0, 0, 1))));
                            this.widgetDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                        }
                    }

                    if (lightingModel.getLightWidgetModel(i).isDistanceWidgetVisible())
                    {
                        this.solidProgram.setUniform("objectID", this.sceneObjectIDLookup.get("Light." + i + ".Distance"));

                        this.solidProgram.setUniform("model_view",
                            Matrix4.translate(arrow3PositionL)
                                .times(Matrix4.scale(perspectiveWidgetScale, perspectiveWidgetScale, 1.0f))
                                .times(Matrix4.fromColumns(
                                    arrow3DirectionX.negated(),
                                    arrow3DirectionY.negated(),
                                    new Vector4(0, 0, 1, 0),
                                    new Vector4(0, 0, 0, 1))));

                        this.widgetDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);

                        if (widgetTransformation.getColumn(3).getXYZ().distance(lightCenter) > 0.01f)
                        {
                            this.solidProgram.setUniform("model_view",
                                Matrix4.translate(arrow3PositionR)
                                    .times(Matrix4.scale(perspectiveWidgetScale, perspectiveWidgetScale, 1.0f))
                                    .times(Matrix4.fromColumns(
                                        arrow3DirectionX,
                                        arrow3DirectionY,
                                        new Vector4(0, 0, 1, 0),
                                        new Vector4(0, 0, 0, 1))));
                            this.widgetDrawable.draw(PrimitiveMode.TRIANGLE_FAN, framebuffer);
                        }
                    }
                }
            }

            context.getState().disableBlending();
            context.getState().enableDepthWrite();
            this.context.getState().enableDepthTest();
        }
    }

    @Override
    public void close()
    {
        if (this.program != null)
        {
            this.program.close();
            this.program = null;
        }

        if (this.environmentBackgroundProgram != null)
        {
            this.environmentBackgroundProgram.close();
            this.environmentBackgroundProgram = null;
        }

        if (this.environmentMap != null)
        {
            this.environmentMap.close();
            this.environmentMap = null;
        }

        if (this.backplateTexture != null)
        {
            this.backplateTexture.close();
            this.backplateTexture = null;
        }

        if (resources != null)
        {
            resources.close();
            resources = null;
        }

        if (shadowMaps != null)
        {
            shadowMaps.close();
            shadowMaps = null;
        }

        if (shadowFramebuffer != null)
        {
            shadowFramebuffer.close();
            shadowFramebuffer = null;
        }

        if (shadowProgram != null)
        {
            shadowProgram.close();
            shadowProgram = null;
        }

        if (screenSpaceDepthFBO != null)
        {
            screenSpaceDepthFBO.close();
            screenSpaceDepthFBO = null;
        }

        if (lightProgram != null)
        {
            lightProgram.close();
            lightProgram = null;
        }

        if (rectangleVertices != null)
        {
            rectangleVertices.close();
            rectangleVertices = null;
        }

        if (lightTexture != null)
        {
            lightTexture.close();
            lightTexture = null;
        }

        if (lightCenterTexture != null)
        {
            lightCenterTexture.close();
            lightCenterTexture = null;
        }

        if (solidProgram != null)
        {
            solidProgram.close();
            solidProgram = null;
        }

        if (widgetVertices != null)
        {
            widgetVertices.close();
            widgetVertices = null;
        }

        if (circleProgram != null)
        {
            circleProgram.close();
            circleProgram = null;
        }

        if (weightBuffer != null)
        {
            weightBuffer.close();
            weightBuffer = null;
        }

        if (simpleTexProgram != null)
        {
            simpleTexProgram.close();
            simpleTexProgram = null;
        }

        if (gridVertices != null)
        {
            gridVertices.close();
            gridVertices = null;
        }

        if (tintedTexProgram != null)
        {
            tintedTexProgram.close();
            tintedTexProgram = null;
        }

        for (FramebufferObject<ContextType> fbo : shadingFramebuffers)
        {
            fbo.close();
        }

        shadingFramebuffers.clear();
    }

    @Override
    public void setLoadingMonitor(LoadingMonitor loadingMonitor)
    {
        this.loadingMonitor = loadingMonitor;
    }

    @Override
    public VertexGeometry getActiveGeometry()
    {
        return this.resources.geometry;
    }

    @Override
    public ViewSet getActiveViewSet()
    {
        return this.resources.viewSet;
    }

    @Override
    public SafeReadonlySettingsModel getSettingsModel()
    {
        return this.settingsModel;
    }

    @Override
    public void setSettingsModel(ReadonlySettingsModel settingsModel)
    {
        this.settingsModel = SafeSettingsModelWrapperFactory.getInstance().wrapUnsafeModel(settingsModel);
    }

    private AbstractImage currentEnvironmentMap;

    @Override
    public Optional<AbstractImage> loadEnvironmentMap(File environmentFile) throws FileNotFoundException
    {
        if (environmentFile == null)
        {
            //noinspection VariableNotUsedInsideIf
            if (this.environmentMap != null)
            {
                this.environmentMapUnloadRequested = true;
            }

            currentEnvironmentMap = null;
            return Optional.empty();
        }
        else if (environmentFile.exists())
        {
            System.out.println("Loading new environment texture.");

            this.desiredEnvironmentFile = environmentFile;
            long lastModified = environmentFile.lastModified();
            boolean readCompleted = false;

            int width = 0;
            int height = 0;
            float[] pixels = null;

            synchronized(loadEnvironmentLock)
            {
                if (Objects.equals(environmentFile, desiredEnvironmentFile) &&
                    (!Objects.equals(environmentFile, currentEnvironmentFile) || lastModified != environmentLastModified))
                {
                    this.loadingMonitor.startLoading();
                    this.loadingMonitor.setMaximum(0.0);

                    try
                    {
                        // Use Michael Ludwig's code to convert to a cube map (supports either cross or panorama input)
                        this.newEnvironmentData = EnvironmentMap.createFromHDRFile(environmentFile);
                        this.currentEnvironmentFile = environmentFile;
                        width = newEnvironmentData.getSide() * 4;
                        height = newEnvironmentData.getSide() * 2;
                        pixels = EnvironmentMap.toPanorama(newEnvironmentData.getData(), newEnvironmentData.getSide(), width, height);
                        readCompleted = true;
                    }
                    catch (FileNotFoundException e)
                    {
                        throw e;
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            this.newEnvironmentDataAvailable = this.newEnvironmentDataAvailable || readCompleted;

            if (readCompleted)
            {
                environmentLastModified = lastModified;
                currentEnvironmentMap = new ArrayBackedImage(width, height, pixels);
            }

            return Optional.ofNullable(currentEnvironmentMap);
        }
        else
        {
            throw new FileNotFoundException(environmentFile.getPath());
        }
    }

    @Override
    public void loadBackplate(File backplateFile) throws FileNotFoundException
    {
        if (backplateFile == null && this.backplateTexture != null)
        {
            this.backplateUnloadRequested = true;
        }
        else if (backplateFile != null && backplateFile.exists())
        {
            System.out.println("Loading new backplate texture.");

            this.desiredBackplateFile = backplateFile;
            long lastModified = backplateFile.lastModified();
            boolean readCompleted = false;

            synchronized(loadBackplateLock)
            {
                if (Objects.equals(backplateFile, desiredBackplateFile) &&
                    (!Objects.equals(backplateFile, currentBackplateFile) || lastModified != backplateLastModified))
                {
                    try
                    {
                        this.newBackplateData = ImageIO.read(backplateFile);
                        this.currentBackplateFile = backplateFile;
                        readCompleted = true;
                    }
                    catch (FileNotFoundException e)
                    {
                        throw e;
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }

            this.newBackplateDataAvailable = this.newBackplateDataAvailable || readCompleted;

            if (readCompleted)
            {
                backplateLastModified = lastModified;
            }
        }
        else if (backplateFile != null)
        {
            throw new FileNotFoundException(backplateFile.getPath());
        }
    }

    @Override
    public String toString()
    {
        return this.id.length() > 32
                ? "..." + this.id.substring(this.id.length()-31)
                : this.id;
    }

    @Override
    public void reloadShaders()
    {
        try
        {
            reloadMainProgram();

            Program<ContextType> newEnvironmentBackgroundProgram = resources.getIBRShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "texture.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "common"), "envbackgroundtexture.frag"))
                    .createProgram();

            if (this.environmentBackgroundProgram != null)
            {
                this.environmentBackgroundProgram.close();
            }

            this.environmentBackgroundProgram = newEnvironmentBackgroundProgram;
            this.environmentBackgroundDrawable = context.createDrawable(environmentBackgroundProgram);
            this.environmentBackgroundDrawable.addVertexBuffer("position", rectangleVertices);


            Program<ContextType> newLightProgram = resources.getIBRShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "imgspace.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "relight"), "light.frag"))
                    .createProgram();

            if (this.lightProgram != null)
            {
                this.lightProgram.close();
            }

            this.lightProgram = newLightProgram;
            this.lightDrawable = context.createDrawable(this.lightProgram);
            this.lightDrawable.addVertexBuffer("position", rectangleVertices);

            Program<ContextType> newWidgetProgram = resources.getIBRShaderProgramBuilder()
                    .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "imgspace.vert"))
                    .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "common"), "solid.frag"))
                    .createProgram();

            if (this.solidProgram != null)
            {
                this.solidProgram.close();
            }

            this.solidProgram = newWidgetProgram;

            this.widgetDrawable = context.createDrawable(this.solidProgram);
            this.widgetDrawable.addVertexBuffer("position", widgetVertices);

            this.gridDrawable = context.createDrawable(this.solidProgram);
            this.gridDrawable.addVertexBuffer("position", gridVertices);

            Program<ContextType> newCircleProgram = resources.getIBRShaderProgramBuilder()
                .addShader(ShaderType.VERTEX, new File(new File(new File("shaders"), "common"), "imgspace.vert"))
                .addShader(ShaderType.FRAGMENT, new File(new File(new File("shaders"), "relight"), "circle.frag"))
                .createProgram();

            if (this.circleProgram != null)
            {
                this.circleProgram.close();
            }

            this.circleProgram = newCircleProgram;

            this.circleDrawable = context.createDrawable(this.circleProgram);
            this.circleDrawable.addVertexBuffer("position", rectangleVertices);
        }
        catch (FileNotFoundException|RuntimeException e)
        {
            e.printStackTrace();
        }
    }

    private void reloadMainProgram(Map<String, Optional<Object>> defineMap, StandardRenderingMode renderingMode) throws FileNotFoundException
    {
        Program<ContextType> newProgram = loadMainProgram(defineMap, renderingMode);

        if (this.program != null)
        {
            this.program.close();
        }

        this.program = newProgram;

        this.lastCompiledRenderingMode = renderingMode;

        this.mainDrawable = context.createDrawable(program);
        this.mainDrawable.addVertexBuffer("position", this.resources.positionBuffer);

        if (this.resources.normalBuffer != null)
        {
            this.mainDrawable.addVertexBuffer("normal", this.resources.normalBuffer);
        }

        if (this.resources.texCoordBuffer != null)
        {
            this.mainDrawable.addVertexBuffer("texCoord", this.resources.texCoordBuffer);
        }

        if (this.resources.tangentBuffer != null)
        {
            this.mainDrawable.addVertexBuffer("tangent", this.resources.tangentBuffer);
        }

        // Also reload ground plane program as it uses the same base shaders.
        if (this.groundPlaneProgram != null)
        {
            this.groundPlaneProgram.close();
            this.groundPlaneProgram = null;
        }

        this.groundPlaneProgram = loadMainProgram(getReferenceScenePreprocessorDefines(), StandardRenderingMode.LAMBERTIAN_SHADED);

        groundPlaneDrawable = context.createDrawable(groundPlaneProgram);
        groundPlaneDrawable.addVertexBuffer("position", rectangleVertices);
        groundPlaneDrawable.setVertexAttrib("normal", new Vector3(0, 0, 1));

        suppressErrors = false;
    }

    private void reloadMainProgram() throws FileNotFoundException
    {
        reloadMainProgram(getPreprocessorDefines(), this.settingsModel == null ?
            StandardRenderingMode.IMAGE_BASED : this.settingsModel.get("renderingMode", StandardRenderingMode.class));
    }

    private Map<String, Optional<Object>> getPreprocessorDefines()
    {
        Map<String, Optional<Object>> defineMap = new HashMap<>(256);

        // Initialize to defaults
        defineMap.put("PHYSICALLY_BASED_MASKING_SHADOWING", Optional.empty());
        defineMap.put("FRESNEL_EFFECT_ENABLED", Optional.empty());
        defineMap.put("SHADOWS_ENABLED", Optional.empty());
        defineMap.put("BUEHLER_ALGORITHM", Optional.empty());
        defineMap.put("SORTING_SAMPLE_COUNT", Optional.empty());
        defineMap.put("RELIGHTING_ENABLED", Optional.empty());
        defineMap.put("VISIBILITY_TEST_ENABLED", Optional.empty());
        defineMap.put("SHADOW_TEST_ENABLED", Optional.empty());
        defineMap.put("PRECOMPUTED_VIEW_WEIGHTS_ENABLED", Optional.empty());
        defineMap.put("USE_VIEW_INDICES", Optional.empty());

        defineMap.put("VIEW_COUNT", Optional.empty());
        defineMap.put("VIRTUAL_LIGHT_COUNT", Optional.empty());
        defineMap.put("ENVIRONMENT_ILLUMINATION_ENABLED", Optional.empty());

        defineMap.put("LUMINANCE_MAP_ENABLED", Optional.of(this.resources.viewSet.hasCustomLuminanceEncoding()));
        defineMap.put("INVERSE_LUMINANCE_MAP_ENABLED", Optional.of(false/*this.resources.viewSet.hasCustomLuminanceEncoding()*/));

        defineMap.put("RAY_DEPTH_GRADIENT", Optional.of(0.1 * this.getScale()));
        defineMap.put("RAY_POSITION_JITTER", Optional.of(0.01 * this.getScale()));

        if (this.settingsModel != null)
        {
            defineMap.put("PHYSICALLY_BASED_MASKING_SHADOWING",
                Optional.of(this.settingsModel.getBoolean("pbrGeometricAttenuationEnabled")));
            defineMap.put("FRESNEL_EFFECT_ENABLED", Optional.of(this.settingsModel.getBoolean("fresnelEnabled")));
            defineMap.put("SHADOWS_ENABLED", Optional.of(this.settingsModel.getBoolean("shadowsEnabled")));

            defineMap.put("BUEHLER_ALGORITHM", Optional.of(this.settingsModel.getBoolean("buehlerAlgorithm")));
            defineMap.put("SORTING_SAMPLE_COUNT", Optional.of(this.settingsModel.getInt("buehlerViewCount")));
            defineMap.put("RELIGHTING_ENABLED", Optional.of(this.settingsModel.getBoolean("relightingEnabled")
                && !settingsModel.getBoolean("lightCalibrationMode") && this.lightingModel != null));

            boolean occlusionEnabled = this.settingsModel.getBoolean("occlusionEnabled")
                && (this.settingsModel.getBoolean("relightingEnabled")
                    || settingsModel.getBoolean("lightCalibrationMode")
                    || this.settingsModel.get("weightMode", ShadingParameterMode.class) != ShadingParameterMode.UNIFORM);

            defineMap.put("VISIBILITY_TEST_ENABLED", Optional.of(occlusionEnabled && this.resources.depthTextures != null));
            defineMap.put("SHADOW_TEST_ENABLED", Optional.of(occlusionEnabled && this.resources.shadowTextures != null
                && !settingsModel.getBoolean("lightCalibrationMode")));

            defineMap.put("PRECOMPUTED_VIEW_WEIGHTS_ENABLED",
                Optional.of(!this.settingsModel.getBoolean("relightingEnabled") && !settingsModel.getBoolean("lightCalibrationMode")
                    && this.settingsModel.get("weightMode", ShadingParameterMode.class) == ShadingParameterMode.UNIFORM));

            if (settingsModel.getBoolean("lightCalibrationMode"))
            {
                defineMap.put("USE_VIEW_INDICES", Optional.of(true));
                defineMap.put("VIEW_COUNT", Optional.of(1));
            }

            if (this.lightingModel != null && this.settingsModel.getBoolean("relightingEnabled"))
            {
                defineMap.put("VIRTUAL_LIGHT_COUNT", Optional.of(lightingModel.getLightCount()));
                defineMap.put("ENVIRONMENT_ILLUMINATION_ENABLED", Optional.of(!Objects.equals(lightingModel.getAmbientLightColor(), Vector3.ZERO)));
                defineMap.put("ENVIRONMENT_TEXTURE_ENABLED", Optional.of(this.environmentMap != null && lightingModel.isEnvironmentMappingEnabled()));
            }
        }

        return defineMap;
    }

    private Map<String, Optional<Object>> getReferenceScenePreprocessorDefines()
    {
        return getPreprocessorDefines();
    }

    private ProgramBuilder<ContextType> getProgramBuilder(Map<String, Optional<Object>> defineMap, StandardRenderingMode renderingMode)
    {
        ProgramBuilder<ContextType> programBuilder = resources.getIBRShaderProgramBuilder(renderingMode);

        for (Entry<String, Optional<Object>> defineEntry : defineMap.entrySet())
        {
            if (defineEntry.getValue().isPresent())
            {
                programBuilder.define(defineEntry.getKey(), defineEntry.getValue().get());
            }
        }

        return programBuilder;
    }

    private Program<ContextType> loadMainProgram(Map<String, Optional<Object>> defineMap, StandardRenderingMode renderingMode) throws FileNotFoundException
    {
        return this.getProgramBuilder(defineMap, renderingMode)
                .define("SPOTLIGHTS_ENABLED", true)
                .addShader(ShaderType.VERTEX, new File("shaders/common/imgspace.vert"))
                .addShader(ShaderType.FRAGMENT, new File("shaders/relight/relight.frag"))
                .createProgram();
    }

    private Program<ContextType> loadMainProgram() throws FileNotFoundException
    {
        return loadMainProgram(getPreprocessorDefines(), this.settingsModel.get("renderingMode", StandardRenderingMode.class));
    }

    private void updateCompiledSettings()
    {
        Map<String, Optional<Object>> defineMap = getPreprocessorDefines();

        StandardRenderingMode renderingMode =
            this.settingsModel == null ? StandardRenderingMode.IMAGE_BASED : this.settingsModel.get("renderingMode", StandardRenderingMode.class);

        if (renderingMode != lastCompiledRenderingMode ||
            defineMap.entrySet().stream().anyMatch(
                defineEntry -> !Objects.equals(this.program.getDefine(defineEntry.getKey()), defineEntry.getValue())))
        {
            try
            {
                System.out.println("Updating compiled render settings.");
                this.reloadMainProgram(defineMap, renderingMode);
            }
            catch (RuntimeException|FileNotFoundException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public SceneViewport getSceneViewportModel()
    {
        return new SceneViewport()
        {
            @Override
            public Object getObjectAtCoordinates(double x, double y)
            {
                if (pixelObjectIDBuffer != null)
                {
                    double xRemapped = Math.min(Math.max(x, 0), 1);
                    double yRemapped = 1.0 - Math.min(Math.max(y, 0), 1);

                    int index = 4 * (int)(Math.round((fboSize.height-1) * yRemapped) * fboSize.width + Math.round((fboSize.width-1) * xRemapped));
                    return sceneObjectNameList[pixelObjectIDBuffer.get(index)];
                }
                else
                {
                    return null;
                }
            }


            private Matrix4 getProjectionInverse()
            {
                Matrix4 projection = getProjectionMatrix(fboSize);
                return  Matrix4.fromRows(
                    new Vector4(1.0f / projection.get(0, 0), 0, 0, 0),
                    new Vector4(0, 1.0f / projection.get(1, 1), 0, 0),
                    new Vector4(0, 0, 0, -1),
                    new Vector4(0, 0, 1.0f, projection.get(2, 2))
                        .dividedBy(projection.get(2, 3)));
            }

            @Override
            public Vector3 get3DPositionAtCoordinates(double x, double y)
            {
                if (pixelDepthBuffer != null)
                {
                    double xRemapped = Math.min(Math.max(x, 0), 1);
                    double yRemapped = 1.0 - Math.min(Math.max(y, 0), 1);

                    int index = (int)(Math.round((fboSize.height-1) * yRemapped) * fboSize.width + Math.round((fboSize.width-1) * xRemapped));

                    Matrix4 projectionInverse = getProjectionInverse();

                    // Transform from screen space into camera space
                    Vector4 unscaledPosition = projectionInverse
                        .times(new Vector4((float)(2 * x - 1), (float)(1 - 2 * y), 2 * (float)(0x0000FFFF & pixelDepthBuffer.get(index)) / (float)0xFFFF - 1, 1.0f));

                    // Transform from camera space into world space.
                    return getCurrentViewMatrix().quickInverse(0.01f)
                            .times(unscaledPosition.getXYZ().dividedBy(unscaledPosition.w).asPosition())
                            .getXYZ().dividedBy(getScale());
                }
                else
                {
                    return null;
                }
            }

            @Override
            public Vector3 getViewingDirection(double x, double y)
            {
                Matrix4 projectionInverse = getProjectionInverse();

                // Take the position the pixel would have at the far clipping plane.
                // Transform from screen space into world space.
                Vector4 unscaledPosition = projectionInverse
                    .times(new Vector4((float)(2 * x - 1), (float)(1 - 2 * y), 1.0f, 1.0f));

                // Transform from camera space into world space.
                // Interpret the vector as the direction from the origin (0,0,0) for this pixel.
                return getCurrentViewMatrix().quickInverse(0.01f)
                    .times(unscaledPosition.getXYZ().dividedBy(unscaledPosition.w).asDirection())
                    .getXYZ().normalized();
            }

            @Override
            public Vector3 getViewportCenter()
            {
                return getCurrentViewMatrix().quickInverse(0.01f)
                    .getColumn(3)
                    .getXYZ().dividedBy(getScale());
            }

            @Override
            public Vector2 projectPoint(Vector3 point)
            {
                Vector4 projectedPoint = getProjectionMatrix(fboSize)
                    .times(getCurrentViewMatrix())
                    .times(point.times(getScale()).asPosition());

                return new Vector2(0.5f + projectedPoint.x / (2 * projectedPoint.w), 0.5f - projectedPoint.y / (2 * projectedPoint.w));
            }

            @Override
            public float getLightWidgetScale()
            {
                return computeLightWidgetScale(getCurrentViewMatrix(), fboSize) / getScale();
            }
        };
    }

    @Override
    public ReadonlyObjectModel getObjectModel()
    {
        return objectModel;
    }

    @Override
    public ReadonlyCameraModel getCameraModel()
    {
        return cameraModel;
    }

    @Override
    public ReadonlyLightingModel getLightingModel()
    {
        return lightingModel;
    }

    @Override
    public void setObjectModel(ReadonlyObjectModel objectModel)
    {
        this.objectModel = objectModel;
    }

    @Override
    public void setCameraModel(ReadonlyCameraModel cameraModel)
    {
        this.cameraModel = cameraModel;
    }

    @Override
    public void setLightingModel(ReadonlyLightingModel lightingModel)
    {
        this.lightingModel = lightingModel;
    }
}
