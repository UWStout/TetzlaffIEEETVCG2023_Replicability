#ifndef ENV_SVD_UNPACK_GLSL
#define ENV_SVD_UNPACK_GLSL

#include "../colorappearance/svd_unpack.glsl"

#define EIGENTEXTURE_RETURN_COUNT 2
#define SECONDARY_EIGENTEXTURE_COUNT 0

#include "environmentweights.glsl"

#line 12 3005

uniform sampler2DArray environmentWeightsTexture;

vec3[4] getWeights(vec2 weightTexCoords[4], int layer)
{
    ivec2 weightTexSize = textureSize(environmentWeightsTexture, 0).xy;
    vec3 returnValues[4];

    for (int i = 0; i < 4; i++)
    {
        ivec2 truncatedTexCoords = min(ivec2(floor(weightTexCoords[i])), weightTexSize - 1);
        vec2 interpolants = weightTexCoords[i] - truncatedTexCoords;

        vec3 weight00 = texelFetch(environmentWeightsTexture, ivec3(truncatedTexCoords, layer), 0).rgb;
        vec3 weight01 = texelFetch(environmentWeightsTexture, ivec3(truncatedTexCoords, layer + 32), 0).rgb;
        vec3 weight10 = texelFetch(environmentWeightsTexture, ivec3(truncatedTexCoords, layer + 64), 0).rgb;
        vec3 weight11 = texelFetch(environmentWeightsTexture, ivec3(truncatedTexCoords, layer + 96), 0).rgb;

        returnValues[i] = mix(
            mix(weight00, weight01, interpolants.y),
            mix(weight10, weight11, interpolants.y),
            interpolants.x);
    }

    return returnValues;
}

vec3 getScaledEnvironmentShadingFromSVD(vec3 normalDir, vec3 specularColorRGB, vec3 roughness)
{
    float mipmapLevel;
#ifdef GL_ARB_texture_query_lod
    mipmapLevel = textureQueryLOD(eigentextures, fTexCoord).x;
#else
    mipmapLevel = 0.0; // TODO better support for graphics cards without textureQueryLOD
#endif

    int mipmapLevelFloor = int(floor(mipmapLevel));
    int mipmapLevelCeil = mipmapLevelFloor + 1;
    float mipmapLevelInterpolant = mipmapLevel - mipmapLevelFloor;

    ivec3 eigentexturesFloorLevelSize = textureSize(eigentextures, mipmapLevelFloor);
    ivec3 eigentexturesCeilLevelSize = textureSize(eigentextures, mipmapLevelCeil);

    ivec3 environmentWeightsSize = textureSize(environmentWeightsTexture, 0);
    vec2 floorToEnv = vec2(eigentexturesFloorLevelSize.xy) / vec2(environmentWeightsSize.xy);
    vec2 ceilToEnv = vec2(eigentexturesCeilLevelSize.xy) / vec2(environmentWeightsSize.xy);

    vec2 texCoordsFloorLevel = fTexCoord * eigentexturesFloorLevelSize.xy;
    ivec2 coords000 = min(ivec2(floor(texCoordsFloorLevel)), eigentexturesFloorLevelSize.xy - ivec2(1));
    ivec2 coords110 = coords000 + 1;
    ivec2 coords010 = ivec2(coords000.x, coords110.y);
    ivec2 coords100 = ivec2(coords110.x, coords000.y);
    vec2 interpolantsFloorLevel = texCoordsFloorLevel - coords000;

    vec2 texCoordsCeilLevel = fTexCoord * eigentexturesCeilLevelSize.xy;
    ivec2 coords001 = min(ivec2(floor(texCoordsCeilLevel)), eigentexturesCeilLevelSize.xy - ivec2(1));
    ivec2 coords111 = coords001 + 1;
    ivec2 coords011 = ivec2(coords001.x, coords111.y);
    ivec2 coords101 = ivec2(coords111.x, coords001.y);
    vec2 interpolantsCeilLevel = texCoordsCeilLevel - coords001;

    vec2 weightCoords[4];
    weightCoords[0] = coords000 / floorToEnv;
    weightCoords[1] = coords010 / floorToEnv;
    weightCoords[2] = coords100 / floorToEnv;
    weightCoords[3] = coords110 / floorToEnv;

    vec3[] weights = getWeights(weightCoords, 0);

    vec3 roughnessSq = roughness * roughness;

    vec3 mfdGeomRoughnessSq =
        mix(mix(weights[0], weights[2], interpolantsFloorLevel.x),
            mix(weights[1], weights[3], interpolantsFloorLevel.x),
            interpolantsFloorLevel.y);

    weights = getWeights(weightCoords, 1);

    vec3 mfdGeomRoughnessSqFresnelFactor =
        mix(mix(weights[0], weights[2], interpolantsFloorLevel.x),
            mix(weights[1], weights[3], interpolantsFloorLevel.x),
            interpolantsFloorLevel.y);

//    float roughnessMono = sqrt(1.0 / (getLuminance(1.0 / roughness * roughness)));
//    EnvironmentResult[EIGENTEXTURE_RETURN_COUNT] shading = computeSVDEnvironmentShading(1, fPosition, normalDir, roughnessMono);
//
//    vec3 mfdGeomRoughnessSq = shading[0].baseFresnel.rgb;
//    vec3 mfdGeomRoughnessSqFresnelFactor = shading[0].fresnelAdjustment.rgb;
//
//    vec4 tex000 = getSignedTexel(ivec3(coords000, 0), mipmapLevelFloor);
//    vec4 tex001 = getSignedTexel(ivec3(coords001, 0), mipmapLevelCeil);
//    vec4 tex010 = getSignedTexel(ivec3(coords010, 0), mipmapLevelFloor);
//    vec4 tex011 = getSignedTexel(ivec3(coords011, 0), mipmapLevelCeil);
//    vec4 tex100 = getSignedTexel(ivec3(coords100, 0), mipmapLevelFloor);
//    vec4 tex101 = getSignedTexel(ivec3(coords101, 0), mipmapLevelCeil);
//    vec4 tex110 = getSignedTexel(ivec3(coords110, 0), mipmapLevelFloor);
//    vec4 tex111 = getSignedTexel(ivec3(coords111, 0), mipmapLevelCeil);
//
//    vec4 tex = mix(mix(mix(tex000, tex100, interpolantsFloorLevel.x),
//                       mix(tex010, tex110, interpolantsFloorLevel.x),
//                       interpolantsFloorLevel.y),
//                   mix(mix(tex001, tex101, interpolantsCeilLevel.x),
//                       mix(tex011, tex111, interpolantsCeilLevel.x),
//                       interpolantsCeilLevel.y),
//                   mipmapLevelInterpolant);
//
//    vec4 blendedTerm = shading[1].baseFresnel * tex;
//    vec3 blendedTermFresnel = shading[1].fresnelAdjustment.rgb * tex.rgb;
//
//    if (blendedTerm.a > 0)
//    {
//        mfdGeomRoughnessSq += blendedTerm.rgb / blendedTerm.a;
//        mfdGeomRoughnessSqFresnelFactor += blendedTermFresnel.rgb / blendedTerm.a;
//    }

    for (int k = 0; k < 15; k++)
    {
        vec4 tex000 = getSignedTexel(ivec3(coords000, k), mipmapLevelFloor);
        vec4 tex001 = getSignedTexel(ivec3(coords001, k), mipmapLevelCeil);
        vec4 tex010 = getSignedTexel(ivec3(coords010, k), mipmapLevelFloor);
        vec4 tex011 = getSignedTexel(ivec3(coords011, k), mipmapLevelCeil);
        vec4 tex100 = getSignedTexel(ivec3(coords100, k), mipmapLevelFloor);
        vec4 tex101 = getSignedTexel(ivec3(coords101, k), mipmapLevelCeil);
        vec4 tex110 = getSignedTexel(ivec3(coords110, k), mipmapLevelFloor);
        vec4 tex111 = getSignedTexel(ivec3(coords111, k), mipmapLevelCeil);

        weights = getWeights(weightCoords, 2 * k + 2);

        vec4 blendedTerm =
            mix(mix(mix(vec4(weights[0] * 2.0 - 1.0, 1.0) * tex000,
                        vec4(weights[2] * 2.0 - 1.0, 1.0) * tex100,
                        interpolantsFloorLevel.x),
                    mix(vec4(weights[1] * 2.0 - 1.0, 1.0) * tex010,
                        vec4(weights[3] * 2.0 - 1.0, 1.0) * tex110,
                        interpolantsFloorLevel.x),
                    interpolantsFloorLevel.y)  ,
                mix(mix(vec4(weights[0] * 2.0 - 1.0, 1.0) * tex001,
                        vec4(weights[2] * 2.0 - 1.0, 1.0) * tex101,
                        interpolantsCeilLevel.x),
                    mix(vec4(weights[1] * 2.0 - 1.0, 1.0) * tex011,
                        vec4(weights[3] * 2.0 - 1.0, 1.0) * tex111,
                        interpolantsCeilLevel.x),
                    interpolantsCeilLevel.y),
                mipmapLevelInterpolant);

        weights = getWeights(weightCoords, 2 * k + 3);

        vec3 blendedTermFresnel =
            mix(mix(mix((weights[0] * 2.0 - 1.0) * tex000.rgb,
                        (weights[2] * 2.0 - 1.0) * tex100.rgb,
                        interpolantsFloorLevel.x),
                    mix((weights[1] * 2.0 - 1.0) * tex010.rgb,
                        (weights[3] * 2.0 - 1.0) * tex110.rgb,
                        interpolantsFloorLevel.x),
                    interpolantsFloorLevel.y),
                mix(mix((weights[0] * 2.0 - 1.0) * tex001.rgb,
                        (weights[2] * 2.0 - 1.0) * tex101.rgb,
                        interpolantsCeilLevel.x),
                    mix((weights[1] * 2.0 - 1.0) * tex011.rgb,
                        (weights[3] * 2.0 - 1.0) * tex111.rgb,
                        interpolantsCeilLevel.x),
                    interpolantsCeilLevel.y),
                mipmapLevelInterpolant);

        if (blendedTerm.w > 0)
        {
            mfdGeomRoughnessSq += blendedTerm.rgb / blendedTerm.a;
            mfdGeomRoughnessSqFresnelFactor += blendedTermFresnel.rgb / blendedTerm.a;
        }
    }

#if FRESNEL_EFFECT_ENABLED
    return mix(mfdGeomRoughnessSqFresnelFactor, mfdGeomRoughnessSq, specularColorRGB);
#else
    return mfdGeomRoughnessSq * specularColorRGB;
#endif
}

#endif // ENV_SVD_UNPACK_GLSL
