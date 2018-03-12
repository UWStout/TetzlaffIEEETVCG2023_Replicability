#ifndef SORT_GLSL
#define SORT_GLSL

#line 5 3002

float computeBuehlerWeight(vec3 targetDirection, vec3 sampleDirection)
{
    return 1.0 / (1.0 - clamp(dot(sampleDirection, targetDirection), 0.0, 0.99999));
}

float getSortingWeight(int virtualIndex, vec3 targetDirection)
{
    mat4 cameraPose = getCameraPose(virtualIndex);
    return computeBuehlerWeight(mat3(cameraPose) * targetDirection, -normalize((cameraPose * vec4(fPosition, 1)).xyz));
}

void sort(int sampleCount, int totalCount, vec3 targetDirection,
    out float[MAX_SORTING_SAMPLE_COUNT] weights, out int[MAX_SORTING_SAMPLE_COUNT] indices)
{
    // Initialization
    for (int i = 0; i < MAX_SORTING_SAMPLE_COUNT && i < sampleCount && i < totalCount; i++)
    {
        weights[i] = -(1.0 / 0.0); // Parentheses needed for AMD cards.
        indices[i] = -1;
    }

    for (int i = totalCount; i < MAX_SORTING_SAMPLE_COUNT && i < sampleCount; i++)
    {
        weights[i] = 0.0; // If there are less samples available than requested, fill in with weights of 0.0.
        indices[i] = 0;
    }

    // Partial heapsort
    for (int i = 0; i < MAX_SORTING_TOTAL_COUNT && i < totalCount; i++)
    {
        float weight = getSortingWeight(i, targetDirection);
        if (weight >= weights[0]) // Decide if the new view goes in the heap
        {
            // Replace the min node in the heap with the new one
            weights[0] = weight;
            indices[0] = i;

            int currentIndex = 0;
            int minIndex = -1;

            while (currentIndex != -1)
            {
                // The two "children" in the heap
                int leftIndex = 2*currentIndex+1;
                int rightIndex = 2*currentIndex+2;

                // Find the smallest of the current node, and its left and right children
                if (leftIndex < sampleCount && weights[leftIndex] < weights[currentIndex])
                {
                    minIndex = leftIndex;
                }
                else
                {
                    minIndex = currentIndex;
                }

                if (rightIndex < sampleCount && weights[rightIndex] < weights[minIndex])
                {
                    minIndex = rightIndex;
                }

                // If a child is smaller than the current node, then swap
                if (minIndex != currentIndex)
                {
                    float weightTmp = weights[currentIndex];
                    int indexTmp = indices[currentIndex];
                    weights[currentIndex] = weights[minIndex];
                    indices[currentIndex] = indices[minIndex];
                    weights[minIndex] = weightTmp;
                    indices[minIndex] = indexTmp;

                    currentIndex = minIndex;
                }
                else
                {
                    currentIndex = -1; // Signal to quit
                }
            }
        }
    }
}

void sortFast(int totalCount, vec3 targetDirection, out float[MAX_SORTING_SAMPLE_COUNT] weights, out int[MAX_SORTING_SAMPLE_COUNT] indices)
{
    float indicesFP[MAX_SORTING_SAMPLE_COUNT];

    // Initialization
    for (int i = 0; i < MAX_SORTING_SAMPLE_COUNT && i < totalCount; i++)
    {
        weights[i] = 0.0;
        indicesFP[i] = -1;
    }

    for (int i = totalCount; i < MAX_SORTING_SAMPLE_COUNT; i++)
    {
        weights[i] = 0.0; // If there are less samples available than requested, fill in with weights of 0.0.
        indicesFP[i] = 0;
    }

    // Partial insertion sort
    for (int i = 0; i < MAX_SORTING_TOTAL_COUNT && i < totalCount; i++)
    {
        float weight = getSortingWeight(i, targetDirection);

        vec2 newValues = mix(vec2(weights[0], indicesFP[0]), vec2(weight, float(i)), max(0, sign(weight - weights[0])));
        weights[0] = newValues[0];
        indicesFP[0] = newValues[1];

        for (int j = 1; j < MAX_SORTING_SAMPLE_COUNT; j++)
        {
            vec4 reorderedValues = mix(
                vec4(weights[j-1], weights[j], indicesFP[j-1], indicesFP[j]),
                vec4(weights[j], weights[j-1], indicesFP[j], indicesFP[j-1]),
                max(0, sign(weights[j-1] - weights[j])));  // 1 if a swap is necessary, 0, otherwise

            weights[j-1] = reorderedValues[0];
            weights[j] = reorderedValues[1];
            indicesFP[j-1] = reorderedValues[2];
            indicesFP[j] = reorderedValues[3];
        }
    }

    for (int i = 0; i < MAX_SORTING_SAMPLE_COUNT; i++)
    {
        indices[i] = int(round(indicesFP[i]));
    }
}

#endif // SORT_GLSL
