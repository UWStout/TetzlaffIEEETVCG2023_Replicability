#ifndef ADJUSTMENT_GLSL
#define ADJUSTMENT_GLSL

#include "../reflectance/reflectance.glsl"

#line 7 2005

//#define SHIFT_FRACTION 0.01171875 // 3/256
#define SHIFT_FRACTION 0.125

uniform sampler2D diffuseEstimate;
uniform sampler2D normalEstimate;
uniform sampler2D specularEstimate;
uniform sampler2D roughnessEstimate;
uniform sampler2D prevSumSqError;

vec3 getDiffuseColor()
{
    return pow(texture(diffuseEstimate, fTexCoord).rgb, vec3(gamma));
}

vec3 getDiffuseNormalVector()
{
    return normalize(texture(normalEstimate, fTexCoord).xyz * 2 - vec3(1,1,1));
}

vec3 getSpecularColor()
{
    return pow(texture(specularEstimate, fTexCoord).rgb, vec3(gamma));
}

float getRoughness()
{
    return texture(roughnessEstimate, fTexCoord).r;
}

struct ParameterizedFit
{
	vec3 diffuseColor;
	vec3 normal;
	vec3 specularColor;
	float roughness;
};

ParameterizedFit adjustFit()
{
	if (texture(prevSumSqError, fTexCoord).x < 1.0)
	{
		discard;
	}
	else
	{
		vec3 geometricNormal = normalize(fNormal);
		vec3 diffuseNormal = getDiffuseNormalVector();
		vec3 prevDiffuseColor = rgbToXYZ(max(vec3(pow(SHIFT_FRACTION, gamma)), getDiffuseColor()));
		vec3 prevSpecularColor = rgbToXYZ(max(vec3(pow(SHIFT_FRACTION, gamma)), getSpecularColor()));
		float prevRoughness = max(SHIFT_FRACTION, getRoughness());
		float roughnessSquared = prevRoughness * prevRoughness;
		float gammaInv = 1.0 / gamma;
		
		// Partitioned matrix:  [ A B ]
		//						[ C D ]
		mat3   mA = mat3(0);
		mat4x3 mB = mat4x3(0);
		mat3x4 mC = mat3x4(0);
		mat4   mD = mat4(0);
		
		vec3 v1 = vec3(0);
		vec4 v2 = vec4(0);
		
		int i = 6;//for (int i = 0; i < 7 && i < viewCount; i++)
		{
			vec3 view = normalize(getViewVector(i));
			float nDotV = max(0, dot(diffuseNormal, view));
			
			// Values of 1.0 for this color would correspond to the expected reflectance
			// for an ideal diffuse reflector (diffuse albedo of 1), which is a reflectance of 1 / pi.
			// Hence, this color corresponds to the reflectance times pi.
			// Both the Phong model and the Cook Torrance with a Beckmann distribution also have a 1/pi factor.
			// By adopting the convention that all reflectance values are scaled by pi in this shader,
			// We can avoid division by pi here as well as avoiding the 1/pi factors in the parameterized models.
			vec4 color = getColor(i);//getLinearColor(i);
			
			if (color.a > 0 && nDotV > 0 && dot(geometricNormal, view) > 0)
			{
				vec3 lightPreNormalized = getLightVector(i);
				vec3 attenuatedLightIntensity = infiniteLightSources ? 
					getLightIntensity(i) : 
					getLightIntensity(i) / (dot(lightPreNormalized, lightPreNormalized));
				vec3 light = normalize(lightPreNormalized);
				float nDotL = max(0, dot(light, diffuseNormal));
				
				vec3 half = normalize(view + light);
				float nDotH = dot(half, diffuseNormal);
				
				if (nDotL > 0.0 && nDotH > 0.0)
				{
					float nDotHSquared = nDotH * nDotH;
						
					float q1 = roughnessSquared + (1.0 - nDotHSquared) / nDotHSquared;
					
					if (q1 > 0.1 && q1 < 10.0) // TODO remove this
					{
						float mfdEval = roughnessSquared / (nDotHSquared * nDotHSquared * q1 * q1);
						
						float q2 = 1.0 + (roughnessSquared - 1.0) * nDotHSquared;
						float mfdDeriv = (1.0 - (roughnessSquared + 1.0) * nDotHSquared) / (q2 * q2 * q2);
						
						float hDotV = max(0, dot(half, view));
						float geomRatio = min(1.0, 2.0 * nDotH * min(nDotV, nDotL) / hDotV) / (4 * nDotV);
						
						vec3 colorScaled = pow(rgbToXYZ(color.rgb / attenuatedLightIntensity), vec3(gammaInv));
						vec3 currentFit = prevDiffuseColor * nDotL + prevSpecularColor * mfdEval * geomRatio;
						vec3 colorResidual = colorScaled - pow(currentFit, vec3(gammaInv)); //pow(prevDiffuseColor * nDotL + 0.667 * prevSpecularColor * mfdEval * geomRatio, vec3(gammaInv)) - pow(currentFit, vec3(gammaInv));
						
						vec3 innerDeriv = gammaInv * pow(currentFit, vec3(gammaInv - 1));
						mat3 innerDerivMatrix = 
							mat3(vec3(innerDeriv.r, 0, 0),
								vec3(0, innerDeriv.g, 0),
								vec3(0, 0, innerDeriv.b));
						mat3 diffuseDerivs = nDotL * innerDerivMatrix;
						mat3 diffuseDerivsTranspose = transpose(mat3(1) * diffuseDerivs); // Workaround for driver bug
						
						mat3 specularReflectivityDerivs = mfdEval * mat3(1);//mfdEval * geomRatio * innerDerivMatrix;
						mat4x3 specularDerivs = mat4x3(
							specularReflectivityDerivs[0],
							specularReflectivityDerivs[1],
							specularReflectivityDerivs[2],
							geomRatio * mfdDeriv * prevSpecularColor * innerDeriv);
						mat3x4 specularDerivsTranspose = transpose(mat3(1) * specularDerivs); // Workaround for driver bug
							
						mA += diffuseDerivsTranspose * diffuseDerivs;
						mB += diffuseDerivsTranspose * specularDerivs;
						mC += specularDerivsTranspose * diffuseDerivs;
						mD += specularDerivsTranspose * specularDerivs;
						
						v1 += /*diffuseDerivsTranspose * */colorResidual;
						v2 += /*specularDerivsTranspose * colorResidual; */ vec4(sign(colorResidual) * abs(colorResidual * vec3(1 / (q1 * q1))), 1.0);
					}
				}
			}
		}
		
		mat3 mAInverse = inverse(mA);
		mat4 schurInverse = inverse(mD - mC * mAInverse * mB);
		
		vec3 diffuseAdj = (mAInverse + mAInverse * mB * schurInverse * mC * mAInverse) * v1 
			- mAInverse * mB * schurInverse * v2;
		
		vec4 specularAdj = -schurInverse * mC * mAInverse * v1 + schurInverse * v2;
		
		// mat3 testIdentity1 = (mAInverse + mAInverse * mB * schurInverse * mC * mAInverse) * mA 
			// - mAInverse * mB * schurInverse * mC;
			
		// mat4x3 testZero1 = (mAInverse + mAInverse * mB * schurInverse * mC * mAInverse) * mB
			// - mAInverse * mB * schurInverse * mD;
			
		// mat3x4 testZero2 = -schurInverse * mC * mAInverse * mA + schurInverse * mC;
		
		// mat4x4 testIdentity2 = -schurInverse * mC * mAInverse * mB + schurInverse * mD;
		
		// vec3 testColor = vec3(1,0,0) * (length(testIdentity1[0]-vec3(1,0,0)) 
				// + length(testIdentity1[1]-vec3(0,1,0))
				// + length(testIdentity1[2]-vec3(0,0,1)))
			// + vec3(0,1,0) * (length(testIdentity2[0]-vec4(1,0,0,0)) 
				// + length(testIdentity2[1]-vec4(0,1,0,0))
				// + length(testIdentity2[2]-vec4(0,0,1,0))
				// + length(testIdentity2[3]-vec4(0,0,0,1)))
			// + vec3(0,0,1) * (length(testZero1[0]) + length(testZero1[1]) + length(testZero1[2]) 
				// + length(testZero1[3]) + length(testZero2[0]) + length(testZero2[1]) 
				// + length(testZero2[2]));
		
		// Attempt to linearize the adjustment scale
		vec3 diffuseAdjLinearized = 
			diffuseAdj * pow(prevDiffuseColor, vec3(gammaInv - 1.0)) * gammaInv;
		vec4 specularAdjLinearized = 
			specularAdj * vec4(pow(prevSpecularColor, vec3(gammaInv - 1.0)) * gammaInv, 1.0);
		float scale = min(SHIFT_FRACTION, SHIFT_FRACTION / 
			sqrt((dot(diffuseAdjLinearized, diffuseAdjLinearized)
				+ dot(specularAdjLinearized, specularAdjLinearized))));
		
		return ParameterizedFit(
			isnan(v1.x) || isinf(v1.x) ? vec3(1,0,0) : vec3(v1.xy, 0) + vec3(0.5),//xyzToRGB(prevDiffuseColor + scale * diffuseAdj), 
			diffuseNormal,
			isnan(v2.x) || isinf(v2.x) ? vec3(1,0,0) : vec3(v2.xy, 0) + vec3(0.5),//xyzToRGB(prevSpecularColor + scale * specularAdj.xyz),
			v2.w + 0.5);//prevRoughness + scale * specularAdj.w);
	}
}

#endif // ADJUSTMENT_GLSL
