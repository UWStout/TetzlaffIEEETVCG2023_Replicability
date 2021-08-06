package tetzlaff.ibrelight.export.PTMfit;

import java.util.Collections;
import java.util.function.*;
import java.util.stream.IntStream;

import tetzlaff.ibrelight.rendering.GraphicsStream;
import tetzlaff.optimization.LeastSquaresMatrixBuilder;
import tetzlaff.util.Counter;

public class PolynormalTextureMapBuilder {
    private final LeastSquaresMatrixBuilder matrixBuilder;
    private final int weightCount=6;
    private final int sampleCount;
    public PolynormalTextureMapBuilder(int width, int height){
        this.sampleCount=width*height*3;
        this.matrixBuilder = new LeastSquaresMatrixBuilder(sampleCount, weightCount,Collections.emptyList(), Collections.emptyList());
    }
    public void buildMatrices(GraphicsStream<LuminaceData> viewStream, PolynormalTextureMapModel PTMmodel, PTMsolution solution)
    {
        IntConsumer sampleValidator = i -> {solution.setWeightsValidity(i,true);};
        matrixBuilder.buildMatrices(viewStream, PTMmodel, sampleValidator);

        //store color in ptm model
        Counter counter = new Counter();
        viewStream.forEach(reflectanceData ->
        {
            // store color for each pixal
            IntStream.range(0, this.sampleCount).parallel().forEach(p ->
            {
                if(p<this.sampleCount/3) PTMmodel.setRedchannel(p%this.sampleCount/3,reflectanceData.getLumin().getRed(p%this.sampleCount/3));
                else if(this.sampleCount/3<=p && p<2*this.sampleCount/3)PTMmodel.setGreenchannel(p%this.sampleCount/3,reflectanceData.getLumin().getGreen(p%this.sampleCount/3));
                else PTMmodel.setBluechannel(p%this.sampleCount/3,reflectanceData.getLumin().getBlue(p%this.sampleCount/3));
            });
            synchronized (counter)
            {
                System.out.println("Finished store color of view " + counter.get() + '.');
                counter.increment();
            }
        });

    }

    public LeastSquaresMatrixBuilder getMatrixBuilder() {
        return matrixBuilder;
    }
}
