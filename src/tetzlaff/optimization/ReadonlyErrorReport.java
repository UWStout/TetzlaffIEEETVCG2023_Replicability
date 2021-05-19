/*
 *  Copyright (c) Michael Tetzlaff 2021
 *  Copyright (c) The Regents of the University of Minnesota 2019
 *
 *  Licensed under GPLv3
 *  ( http://www.gnu.org/licenses/gpl-3.0.html )
 *
 *  This code is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 */

package tetzlaff.optimization;

public class ReadonlyErrorReport
{
    private double previousError = Double.POSITIVE_INFINITY;
    private double error = Double.POSITIVE_INFINITY;
    private final int sampleCount;

    public ReadonlyErrorReport(int sampleCount)
    {
        this.sampleCount = sampleCount;
    }

    public double getPreviousError()
    {
        return previousError;
    }

    public double getError()
    {
        return error;
    }

    public int getSampleCount() { return sampleCount; }

    protected void setError(double newError)
    {
        this.previousError = this.error;
        this.error = newError;
    }

    protected void reject()
    {
        // Roll back to previous error calculation.
        error = previousError;
    }
}
