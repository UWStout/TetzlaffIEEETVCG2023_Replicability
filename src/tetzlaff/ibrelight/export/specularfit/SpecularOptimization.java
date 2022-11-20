/*
 *  Copyright (c) Michael Tetzlaff 2022
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.ibrelight.export.specularfit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.stream.IntStream;

import tetzlaff.gl.builders.ProgramBuilder;
import tetzlaff.gl.core.*;
import tetzlaff.gl.vecmath.DoubleVector3;
import tetzlaff.gl.vecmath.Vector3;
import tetzlaff.gl.vecmath.Vector4;
import tetzlaff.ibrelight.core.Projection;
import tetzlaff.ibrelight.rendering.resources.GraphicsStream;
import tetzlaff.ibrelight.rendering.resources.GraphicsStreamResource;
import tetzlaff.ibrelight.rendering.resources.IBRResources;
import tetzlaff.optimization.ReadonlyErrorReport;
import tetzlaff.optimization.ShaderBasedErrorCalculator;
import tetzlaff.optimization.function.GeneralizedSmoothStepBasis;
import tetzlaff.optimization.function.StepBasis;
import tetzlaff.util.ColorList;

/**
 * Implement specular fit using algorithm described by Nam et al., 2018
 */
public class SpecularOptimization
{
    static final boolean DEBUG = false;

    private final SpecularFitSettings settings;

    public SpecularOptimization(SpecularFitSettings settings)
    {
        this.settings = settings;
    }

    public <ContextType extends Context<ContextType>> SpecularFit<ContextType> createFit(IBRResources<ContextType> resources)
        throws IOException
    {
        Instant start = Instant.now();

        // Get GPU context and disable back face culling since we're rendering in texture space
        ContextType context = resources.context;
        context.getState().disableBackFaceCulling();
        SpecularFitProgramFactory<ContextType> programFactory = new SpecularFitProgramFactory<>(resources, settings);

        // Calculate reasonable image resolution for error calculation
        Projection defaultProj = resources.viewSet.getCameraProjection(resources.viewSet.getCameraProjectionIndex(
            resources.viewSet.getPrimaryViewIndex()));

        int imageWidth;
        int imageHeight;

        if (defaultProj.getAspectRatio() < 1.0)
        {
            imageWidth = settings.width;
            imageHeight = Math.round(imageWidth / defaultProj.getAspectRatio());
        }
        else
        {
            imageHeight = settings.height;
            imageWidth = Math.round(imageHeight * defaultProj.getAspectRatio());
        }

        // Create space for the solution.
        SpecularFitSolution solution = new SpecularFitSolution(settings);

        // Initialize weights using K-means.
        new SpecularFitInitializer<>(resources, settings).initialize(solution);

        // Complete "specular fit": includes basis representation on GPU, roughness / reflectivity fit, normal fit, and final diffuse fit.
        SpecularFit<ContextType> specularFit = new SpecularFit<>(context, resources, settings);

        try
        (
            // Reflectance stream: includes a shader program and a framebuffer object for extracting reflectance data from images.
            GraphicsStreamResource<ContextType> reflectanceStream = resources.streamAsResource(
                getReflectanceProgramBuilder(programFactory),
                context.buildFramebufferObject(settings.width, settings.height)
                    .addColorAttachment(ColorFormat.RGBA32F)
                    .addColorAttachment(ColorFormat.RGBA32F));

            // Compare fitted models against actual photographs
            Program<ContextType> errorCalcProgram = createErrorCalcProgram(programFactory);

            // Framebuffer for calculating error and reconstructing 3D renderings of the object
            FramebufferObject<ContextType> scratchFramebuffer =
                context.buildFramebufferObject(imageWidth, imageHeight)
                    .addColorAttachment(ColorFormat.RGBA32F)
                    .addDepthAttachment()
                    .createFramebufferObject()
        )
        {
            // Setup reflectance extraction program
            programFactory.setupShaderProgram(reflectanceStream.getProgram());
            reflectanceStream.getProgram().setTexture("roughnessEstimate", specularFit.getSpecularRoughnessMap());

            Drawable<ContextType> errorCalcDrawable = resources.createDrawable(errorCalcProgram);
            specularFit.basisResources.useWithShaderProgram(errorCalcProgram);
            errorCalcProgram.setTexture("roughnessEstimate", specularFit.getSpecularRoughnessMap());
            errorCalcProgram.setUniform("errorGamma", 1.0f);

            // Track how the error improves over iterations of the whole algorithm.
            double previousIterationError;

            BRDFReconstruction brdfReconstruction = new BRDFReconstruction(
                settings,
                new GeneralizedSmoothStepBasis(
                    settings.microfacetDistributionResolution,
                    settings.getMetallicity(),
                    (int)Math.round(settings.getSpecularSmoothness() * settings.microfacetDistributionResolution),
                    x -> 3*x*x-2*x*x*x)
//                new StepBasis(settings.microfacetDistributionResolution, settings.getMetallicity())
            );
            SpecularWeightOptimization weightOptimization = new SpecularWeightOptimization(settings);
            ShaderBasedErrorCalculator errorCalculator = new ShaderBasedErrorCalculator(settings.width * settings.height);

            // Instantiate once so that the memory buffers can be reused.
            GraphicsStream<ColorList[]> reflectanceStreamParallel = reflectanceStream.parallel();

//            reflectanceStream.getProgram().setTexture("normalEstimate", specularFit.getNormalMap());
//
//            // First index is basis
//            // Second index covers the different totals: [0, 3]: diffuse RGB + weight; [4, 7]: specular RGB + weight;
//            // 8: multiplier used to subtract diffuse from specular
//            // TODO this could all be moved into a shader for better performance
//            float[][] finalTotals = reflectanceStreamParallel.map(framebufferData -> new ReflectanceData(framebufferData[0], framebufferData[1]))
//                .collect(() -> IntStream.range(0, settings.basisCount).mapToObj(b -> new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0 }).toArray(float[][]::new),
//                    (totals, frame) -> IntStream.range(0, frame.size()) // For each pixel in the frame
//                        .filter(p -> frame.getVisibility(p) > 0.0)
//                        .forEach(p -> IntStream.range(0, settings.basisCount).boxed() // For each basis function
//                            // Determine which basis function a pixel corresponds to in the initial mapping
//                            .max(Comparator.comparingDouble(b -> solution.getWeights(p).get(b)))
//                            .ifPresent(b ->
//                            {
//                                float weight = frame.getAdditionalWeight(p);
//                                if (frame.getHalfwayIndex(p) < 1.0)
//                                {
//                                    float pseudoBRDF = (1 - frame.getHalfwayIndex(p)) * frame.getGeomRatio(p);
//                                    totals[b][4] += frame.getRed(p) * pseudoBRDF * weight;
//                                    totals[b][5] += frame.getGreen(p) * pseudoBRDF * weight;
//                                    totals[b][6] += frame.getBlue(p) * pseudoBRDF * weight;
//                                    totals[b][7] += pseudoBRDF * pseudoBRDF * weight;
//                                    totals[b][8] += pseudoBRDF * weight;
//                                }
//                                else
//                                {
//                                    totals[b][0] += frame.getRed(p) * weight;
//                                    totals[b][1] += frame.getGreen(p) * weight;
//                                    totals[b][2] += frame.getBlue(p) * weight;
//                                    totals[b][3] += weight;
//                                }
//                            })));
//
//            for (int b = 0; b < settings.basisCount; b++)
//            {
////                DoubleVector3 diffuseReflectance = new DoubleVector3(
////                    finalTotals[b][0] / finalTotals[b][3], finalTotals[b][1] / finalTotals[b][3], finalTotals[b][2] / finalTotals[b][3]);
//                DoubleVector3 diffuseReflectance = DoubleVector3.ZERO;
//                solution.setDiffuseAlbedo(b, diffuseReflectance.times(Math.PI)); // Mutiply times pi to go from reflectance to albedo
//
//                // Specular Avg = (Total weighted reflectance - Diffuse color * Total weighted count) / Total weighted pseudo-BRDF
//                DoubleVector3 specularScale = new DoubleVector3(
//                    Math.max(0, finalTotals[b][4] - diffuseReflectance.x * finalTotals[b][8]) / finalTotals[b][7],
//                    Math.max(0, finalTotals[b][5] - diffuseReflectance.y * finalTotals[b][8]) / finalTotals[b][7],
//                    Math.max(0, finalTotals[b][6] - diffuseReflectance.z * finalTotals[b][8]) / finalTotals[b][7]
//                );
//
//                // Initialize the specular basis functions to in turn generate a better initial guess for the normal map.
//                for (int m = 0; m < settings.microfacetDistributionResolution; m++)
//                {
//                    float psuedoNDF = 1 - (float)m / (float)settings.microfacetDistributionResolution;
//
//                    solution.getSpecularRed().set(m, b, psuedoNDF * specularScale.x);
//                    solution.getSpecularGreen().set(m, b, psuedoNDF * specularScale.y);
//                    solution.getSpecularBlue().set(m, b, psuedoNDF * specularScale.z);
//                }
//            }
//
//            // TODO move to a function to eliminate copy-pasted code
//
//            // Prepare for error calculation and normal estimation on the GPU.
//            specularFit.basisResources.updateFromSolution(solution);
//
//            // Calculate the error in preparation for normal estimation.
//            errorCalculator.update(errorCalcDrawable, scratchFramebuffer);
//
//            // Log error in debug mode.
//            if (DEBUG)
//            {
//                System.out.println("Calculating error...");
//                logError(errorCalculator.getReport());
//
//                // Save basis image visualization for reference and debugging
//                try(BasisImageCreator<ContextType> basisImageCreator = new BasisImageCreator<>(context, settings))
//                {
//                    basisImageCreator.createImages(specularFit);
//                }
//
//                // write out diffuse texture for debugging
//                solution.saveDiffuseMap(settings.additional.getFloat("gamma"));
//            }
//
//            if (settings.isNormalRefinementEnabled())
//            {
//                System.out.println("Optimizing normals...");
//
//                specularFit.normalOptimization.execute(normalMap ->
//                    {
//                        // Update program to use the new front buffer for error calculation.
//                        errorCalcProgram.setTexture("normalEstimate", normalMap);
//
//                        if (DEBUG)
//                        {
//                            System.out.println("Calculating error...");
//                        }
//
//                        // Calculate the error to determine if we should stop.
//                        errorCalculator.update(errorCalcDrawable, scratchFramebuffer);
//
//                        if (DEBUG)
//                        {
//                            // Log error in debug mode.
//                            logError(errorCalculator.getReport());
//                        }
//
//                        return errorCalculator.getReport();
//                    },
//                    settings.getConvergenceTolerance());
//
//                if (errorCalculator.getReport().getError() > errorCalculator.getReport().getPreviousError())
//                {
//                    // Revert error calculations to the last accepted result.
//                    errorCalculator.reject();
//                }
//            }

            do
            {
                previousIterationError = errorCalculator.getReport().getError();

                // Use the current front normal buffer for extracting reflectance information.
                reflectanceStream.getProgram().setTexture("normalEstimate", specularFit.getNormalMap());

                // Reconstruct the basis BRDFs.
                // Set up a stream and pass it to the BRDF reconstruction module to give it access to the reflectance information.
                // Operate in parallel for optimal performance.
                brdfReconstruction.execute(
                    reflectanceStreamParallel.map(framebufferData -> new ReflectanceData(framebufferData[0], framebufferData[1])),
                    solution);

                // Use the current front normal buffer for calculating error.
                errorCalcProgram.setTexture("normalEstimate", specularFit.getNormalMap());

                // Log error in debug mode.
                if (DEBUG)
                {
                    // Prepare for error calculation on the GPU.
                    // Basis functions will have changed.
                    specularFit.basisResources.updateFromSolution(solution);

                    System.out.println("Calculating error...");
                    errorCalculator.update(errorCalcDrawable, scratchFramebuffer);
                    logError(errorCalculator.getReport());

                    // Save basis image visualization for reference and debugging
                    try(BasisImageCreator<ContextType> basisImageCreator = new BasisImageCreator<>(context, settings))
                    {
                        basisImageCreator.createImages(specularFit);
                    }

                    // write out diffuse texture for debugging
                    solution.saveDiffuseMap(settings.additional.getFloat("gamma"));
                }

                if (settings.basisCount > 1)
                {
                    // Make sure there are enough blocks for any pixels that don't go into the weight blocks evenly.
                    int blockCount = (settings.width * settings.height + settings.getWeightBlockSize() - 1) / settings.getWeightBlockSize();

                    // Initially assume that all texels are invalid.
                    solution.invalidateWeights();

                    for (int i = 0; i < blockCount; i++) // TODO: this was done quickly; may need to be refactored
                    {
                        System.out.println("Starting block " + i + "...");
                        weightOptimization.execute(
                            reflectanceStream.map(framebufferData -> new ReflectanceData(framebufferData[0], framebufferData[1])),
                            solution, i * settings.getWeightBlockSize());
                    }
                }

                if (DEBUG)
                {
                    System.out.println("Calculating error...");
                }

                // Prepare for error calculation and then normal optimization on the GPU.
                // Weight maps will have changed.
                specularFit.basisResources.updateFromSolution(solution);

                // Calculate the error in preparation for normal estimation.
                errorCalculator.update(errorCalcDrawable, scratchFramebuffer);

                if (DEBUG)
                {
                    // Log error in debug mode.
                    logError(errorCalculator.getReport());
                }

                if (settings.isNormalRefinementEnabled())
                {
                    System.out.println("Optimizing normals...");

                    specularFit.normalOptimization.execute(normalMap ->
                    {
                        // Update program to use the new front buffer for error calculation.
                        errorCalcProgram.setTexture("normalEstimate", normalMap);

                        if (DEBUG)
                        {
                            System.out.println("Calculating error...");
                        }

                        // Calculate the error to determine if we should stop.
                        errorCalculator.update(errorCalcDrawable, scratchFramebuffer);

                        if (DEBUG)
                        {
                            // Log error in debug mode.
                            logError(errorCalculator.getReport());
                        }

                        return errorCalculator.getReport();
                    },
                    settings.getConvergenceTolerance());

                    if (errorCalculator.getReport().getError() > errorCalculator.getReport().getPreviousError())
                    {
                        // Revert error calculations to the last accepted result.
                        errorCalculator.reject();
                    }
                }

                // Estimate specular roughness and reflectivity.
                // This can cause error to increase but it's unclear if that poses a problem for convergence.
                specularFit.roughnessOptimization.execute();

                if (DEBUG)
                {
                    specularFit.roughnessOptimization.saveTextures();

                    // Log error in debug mode.
                    specularFit.basisResources.updateFromSolution(solution);
                    System.out.println("Calculating error...");
                    errorCalculator.update(errorCalcDrawable, scratchFramebuffer);
                    logError(errorCalculator.getReport());
                }
            }
            while ((settings.basisCount > 1 || settings.isNormalRefinementEnabled()) &&
                // Iteration not necessary if basisCount is 1 and normal refinement is off.
                previousIterationError - errorCalculator.getReport().getError() > settings.getConvergenceTolerance());

            // Calculate final diffuse map without the constraint of basis functions.
            specularFit.diffuseOptimization.execute(specularFit);

            Duration duration = Duration.between(start, Instant.now());
            System.out.println("Total processing time: " + duration);

            try(PrintStream time = new PrintStream(new File(settings.outputDirectory, "time.txt")))
            {
                time.println(duration);
            }
            catch (FileNotFoundException e)
            {
                e.printStackTrace();
            }

            // Save the final diffuse and normal maps
            specularFit.diffuseOptimization.saveDiffuseMap();
            specularFit.normalOptimization.saveNormalMap();

            // Save the final basis functions
            solution.saveBasisFunctions();

            // Save basis image visualization for reference and debugging
            try(BasisImageCreator<ContextType> basisImageCreator = new BasisImageCreator<>(context, settings))
            {
                basisImageCreator.createImages(specularFit);
            }

            // Fill holes in weight maps and calculate some final error statistics.
            new SpecularFitFinalizer(settings)
                .execute(solution, resources, specularFit, scratchFramebuffer, errorCalculator.getReport(), errorCalcDrawable);

            return specularFit;
        }
    }

    private static void logError(ReadonlyErrorReport report)
    {
        System.out.println("--------------------------------------------------");
        System.out.println("Error: " + report.getError());
        System.out.println("(Previous error: " + report.getPreviousError() + ')');
        System.out.println("--------------------------------------------------");
        System.out.println();
    }

    private static <ContextType extends Context<ContextType>>
    ProgramBuilder<ContextType> getReflectanceProgramBuilder(SpecularFitProgramFactory<ContextType> programFactory)
    {
        return programFactory.getShaderProgramBuilder(
            new File("shaders/common/texspace_noscale.vert"),
            new File("shaders/specularfit/extractReflectance.frag"));
    }

    private static <ContextType extends Context<ContextType>>
    Program<ContextType> createErrorCalcProgram(SpecularFitProgramFactory<ContextType> programFactory) throws FileNotFoundException
    {
        return programFactory.createProgram(
            new File("shaders/common/texspace_noscale.vert"),
            new File("shaders/specularfit/errorCalc.frag"),
            false); // Disable visibility and shadow tests for error calculation.
    }
}
