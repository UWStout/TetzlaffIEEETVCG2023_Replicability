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
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.stream.IntStream;

import org.ejml.data.DMatrixRMaj;
import org.ejml.simple.SimpleMatrix;
import tetzlaff.optimization.function.BasisFunctions;
import tetzlaff.optimization.function.MatrixBuilder;
import tetzlaff.optimization.function.MatrixBuilderSample;
import tetzlaff.optimization.MatrixSystem;

import static org.ejml.dense.row.CommonOps_DDRM.multTransA;

/**
 * A helper class to maintain state necessary to efficiently build the matrix that can solve for reflectance.
 */
final class ReflectanceMatrixBuilder
{
    // Set to true to validate the MatrixBuilder implementation (should generally be turned off for much better efficiency).
    private static final boolean VALIDATE = false;

    static final boolean DUMP_SAMPLES = false;

    /**
     * Reflectance information for all the data.
     */
    private final ReflectanceData reflectanceData;

    /**
     * Weight solution from the previous iteration.
     */
    private final SpecularFitSolution solution;

    /**
     * Settings to be used for the specular fit; associated with the SpecularFitSolution.
     */
    private final SpecularFitSettings settings;

    /**
     * Underlying matrix builder utility.
     */
    private final MatrixBuilder matrixBuilder;

    /**
     * Stores both the LHS and RHS of the system to be solved.
     * LHS = A'A
     * RHS = A'y, possibly for multiple channels (i.e. red, green, blue for color).
     */
    private MatrixSystem contribution;

    /**
     * Construct by accepting matrices where the final results will be stored.
     */
    ReflectanceMatrixBuilder(ReflectanceData reflectanceData, SpecularFitSolution solution,
                             double metallicity, BasisFunctions stepBasis, MatrixSystem contribution)
    {
        this.solution = solution;

        //noinspection AssignmentOrReturnOfFieldWithMutableType
        this.reflectanceData = reflectanceData;

        this.contribution = contribution;

        settings = solution.getSettings();

        // Initialize running totals
        matrixBuilder = new MatrixBuilder(settings.basisCount, 3, metallicity, stepBasis, contribution);
    }

    public void execute()
    {
        try (PrintStream sampleDump = DUMP_SAMPLES ?
            new PrintStream(new FileOutputStream(new File(settings.outputDirectory, "sampleDump.txt"), true)) : null)
        {
            matrixBuilder.build(
                IntStream.range(0, reflectanceData.size())
                    .filter(p -> reflectanceData.getVisibility(p) > 0) // Eliminate pixels without valid samples
                    .mapToObj(p ->
                    {
                        MatrixBuilderSample sample = new MatrixBuilderSample(
                            reflectanceData.getHalfwayIndex(p) * settings.microfacetDistributionResolution,
                            matrixBuilder.getBasisLibrary(), reflectanceData.getGeomRatio(p),
                            reflectanceData.getAdditionalWeight(p), b -> solution.getWeights(p).get(b),
                            reflectanceData.getRed(p), reflectanceData.getGreen(p), reflectanceData.getBlue(p));

                        if (DUMP_SAMPLES)
                        {
                            sampleDump.print(sample.actual + ": " + sample.weightByInstance.applyAsDouble(0));

                            for (int i = 1; i < settings.basisCount; i++)
                            {
                                sampleDump.print(", " + sample.weightByInstance.applyAsDouble(i));
                            }

                            sampleDump.println(": " + sample.observed[0] + ", " + sample.observed[1] + ", " + sample.observed[2]);
                        }

                        return sample;
                    }));
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }

        if (VALIDATE)
        {
            validate();
        }
    }

    private void validate()
    {
        // Calculate the matrix products the slow way to make sure that the implementation is correct.
        SimpleMatrix mA = new SimpleMatrix(reflectanceData.size(),
                settings.basisCount * (settings.microfacetDistributionResolution + 1), DMatrixRMaj.class);
        SimpleMatrix yRed = new SimpleMatrix(reflectanceData.size(), 1);
        SimpleMatrix yGreen = new SimpleMatrix(reflectanceData.size(), 1);
        SimpleMatrix yBlue = new SimpleMatrix(reflectanceData.size(), 1);

        for (int p = 0; p < reflectanceData.size(); p++)
        {
            if (reflectanceData.getVisibility(p) > 0)
            {
                float halfwayIndex = reflectanceData.getHalfwayIndex(p);
                float geomRatio = reflectanceData.getGeomRatio(p);

                // square-root since we're minimizing the sum of w * | y - A x |^2, not w^2 * | y - A x |^2
                float addlWeight = (float)Math.sqrt(reflectanceData.getAdditionalWeight(p));

                // Calculate which discretized MDF element the current sample belongs to.
                double mExact = halfwayIndex * settings.microfacetDistributionResolution;
                int mFloor = Math.min(settings.microfacetDistributionResolution - 1, (int) Math.floor(mExact));

                yRed.set(p, addlWeight * reflectanceData.getRed(p));
                yGreen.set(p, addlWeight * reflectanceData.getGreen(p));
                yBlue.set(p, addlWeight * reflectanceData.getBlue(p));

                // When floor and exact are the same, t = 1.0.  When exact is almost a whole increment greater than floor, t approaches 0.0.
                // If mFloor is clamped to MICROFACET_DISTRIBUTION_RESOLUTION -1, then mExact will be much larger, so t = 0.0.
                double t = Math.max(0.0, 1.0 + mFloor - mExact);

                double diffuseFactor = matrixBuilder.getMetallicity() * geomRatio + (1 - matrixBuilder.getMetallicity());

                for (int b = 0; b < settings.basisCount; b++)
                {
                    // diffuse
                    mA.set(p, b, addlWeight * solution.getWeights(p).get(b) * diffuseFactor);

                    // specular
                    if (mExact < settings.microfacetDistributionResolution)
                    {
                        // Iterate over the available step functions in the basis.
                        for (int s = 0; s < settings.microfacetDistributionResolution; s++)
                        {
                            // Evaluate each step function twice, to the left and right of the current sample.
                            double fFloor = matrixBuilder.getBasisLibrary().evaluate(s, mFloor);
                            double fCeil = matrixBuilder.getBasisLibrary().evaluate(s, mFloor + 1);

                            // Blend between the two sampled locations.
                            // In the case of a simple step function, fFloor & fCeil will both be either 1 or 0
                            // except at a boundary, where fFloor will be 1 and fCeil will be 0.
                            double fInterp = fFloor * t + fCeil * (1 - t);

                            // Index of the column where the coefficient will be stored in the big matrix.
                            int j = settings.basisCount * (s + 1) + b;

                            // specular with blending between the two sampled locations.
                            mA.set(p, j, addlWeight * geomRatio * solution.getWeights(p).get(b) * fInterp);
                        }
                    }
                }
            }
        }

        SimpleMatrix mATA = new SimpleMatrix(mA.numCols(), mA.numCols());
        SimpleMatrix vATyRed = new SimpleMatrix(mA.numCols(), 1);
        SimpleMatrix vATyGreen = new SimpleMatrix(mA.numCols(), 1);
        SimpleMatrix vATyBlue = new SimpleMatrix(mA.numCols(), 1);

        // Low level operations to avoid using unnecessary memory.
        multTransA(mA.getMatrix(), mA.getMatrix(), mATA.getMatrix());
        multTransA(mA.getMatrix(), yRed.getMatrix(), vATyRed.getMatrix());
        multTransA(mA.getMatrix(), yGreen.getMatrix(), vATyGreen.getMatrix());
        multTransA(mA.getMatrix(), yBlue.getMatrix(), vATyBlue.getMatrix());

        for (int i = 0; i < mATA.numRows(); i++)
        {
            assert Math.abs(vATyRed.get(i, 0) - contribution.rhs[0].get(i, 0)) <= vATyRed.get(i, 0) * 0.001 : "Red " + i;
            assert Math.abs(vATyGreen.get(i, 0) - contribution.rhs[1].get(i, 0)) <= vATyGreen.get(i, 0) * 0.001 : "Green  " + i;
            assert Math.abs(vATyBlue.get(i, 0) - contribution.rhs[2].get(i, 0)) <= vATyBlue.get(i, 0) * 0.001 : "Blue  " + i;

            for (int j = 0; j < mATA.numCols(); j++)
            {
                assert Math.abs(mATA.get(i, j) - contribution.lhs.get(i, j)) <= mATA.get(i, j) * 0.001 : "Matrix " + i + " " + j;
            }
        }
    }
}
