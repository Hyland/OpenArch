/*
 * (C) Copyright 2025-2026 Hyland (https://www.hyland.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nuxeo.rest.query.test;

import static jakarta.ws.rs.core.Response.Status.OK;
import static java.util.stream.Collectors.toList;
import static org.nuxeo.ecm.core.model.EmbeddingModel.VECTOR_SIZE;
import static org.nuxeo.rest.query.test.RestQueryMultipleEmbeddingsFeature.EMBEDDING_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.nuxeo.ecm.core.api.local.WithUser;
import org.nuxeo.rest.query.QueryAPI;
import org.nuxeo.runtime.test.runner.Features;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.ws.rs.core.Response.Status;

@SpringBootTest(classes = { QueryAPI.class })
@Tag("useOpenSearch")
@Features(RestQueryMultipleEmbeddingsFeature.class)
@Import({ QueryAPITestConfiguration.class })
public class EmbeddingQueryAPITest extends AbstractQueryAPITest {

    static final List<Double> VECTOR = List.of(0.402, 0.539, 0.676, 0.813, 0.950, 0.087, 0.224, 0.361, 0.498, 0.635,
            0.772, 0.909, 0.046, 0.183, 0.320, 0.457, 0.594, 0.731, 0.868, 0.605, 0.142, 0.279, 0.416, 0.553, 0.690,
            0.827, 0.964, 0.101, 0.238, 0.375, 0.512, 0.649, 0.786, 0.923, 0.060, 0.197, 0.334, 0.471, 0.608, 0.745,
            0.882, 0.019, 0.156, 0.293, 0.430, 0.567, 0.704, 0.841, 0.578, 0.115, 0.252, 0.389, 0.526, 0.663, 0.800,
            0.937, 0.074, 0.211, 0.348, 0.485, 0.622, 0.759, 0.896, 0.033, 0.170, 0.307, 0.444, 0.581, 0.718, 0.855,
            0.592, 0.129, 0.266, 0.403, 0.540, 0.677, 0.814, 0.951, 0.088, 0.225, 0.362, 0.499, 0.636, 0.773, 0.910,
            0.047, 0.184, 0.321, 0.458, 0.595, 0.732, 0.869, 0.606, 0.143, 0.280, 0.417, 0.554, 0.691, 0.828, 0.965,
            0.102, 0.239, 0.376, 0.513, 0.650, 0.787, 0.924, 0.061, 0.198, 0.335, 0.472, 0.609, 0.746, 0.883, 0.020,
            0.157, 0.294, 0.431, 0.568, 0.705, 0.842, 0.579, 0.116, 0.253, 0.390, 0.527, 0.664, 0.801, 0.938, 0.075,
            0.212, 0.349, 0.486, 0.623, 0.760, 0.897, 0.034, 0.171, 0.308, 0.445, 0.582, 0.719, 0.856, 0.593, 0.130,
            0.267, 0.404, 0.541, 0.678, 0.815, 0.952, 0.089, 0.226, 0.363, 0.500, 0.637, 0.774, 0.911, 0.048, 0.185,
            0.322, 0.459, 0.596, 0.733, 0.870, 0.607, 0.144, 0.281, 0.418, 0.555, 0.692, 0.829, 0.966, 0.103, 0.240,
            0.377, 0.514, 0.651, 0.788, 0.925, 0.062, 0.199, 0.336, 0.473, 0.610, 0.747, 0.884, 0.021, 0.158, 0.295,
            0.432, 0.569, 0.706, 0.843, 0.580, 0.117, 0.254, 0.391, 0.528, 0.665, 0.802, 0.939, 0.076, 0.213, 0.350,
            0.487, 0.624, 0.761, 0.898, 0.035, 0.172, 0.309, 0.446, 0.583, 0.720, 0.857, 0.594, 0.131, 0.268, 0.405,
            0.542, 0.679, 0.816, 0.953, 0.090, 0.227, 0.364, 0.501, 0.638, 0.775, 0.912, 0.049, 0.186, 0.323, 0.460,
            0.597, 0.734, 0.871, 0.608, 0.145, 0.282, 0.419, 0.556, 0.693, 0.830, 0.967, 0.104, 0.241, 0.378, 0.515,
            0.652, 0.789, 0.926, 0.063, 0.200, 0.337, 0.474, 0.611, 0.748, 0.885, 0.022, 0.159, 0.296, 0.433, 0.570,
            0.707, 0.844, 0.581, 0.118, 0.255, 0.392, 0.529, 0.666, 0.803, 0.940, 0.077, 0.214, 0.351, 0.488, 0.625,
            0.762, 0.899, 0.036, 0.173, 0.310, 0.447, 0.584, 0.721, 0.858, 0.595, 0.132, 0.269, 0.406, 0.543, 0.680,
            0.817, 0.954, 0.091, 0.228, 0.365, 0.502, 0.639, 0.776, 0.913, 0.050, 0.187, 0.324, 0.461, 0.598, 0.735,
            0.872, 0.609, 0.146, 0.283, 0.420, 0.557, 0.694, 0.831, 0.968, 0.105, 0.242, 0.379, 0.516, 0.653, 0.790,
            0.927, 0.064, 0.201, 0.338, 0.475, 0.612, 0.749, 0.886, 0.023, 0.160, 0.297, 0.434, 0.571, 0.708, 0.845,
            0.582, 0.119, 0.256, 0.393, 0.530, 0.667, 0.804, 0.941, 0.078, 0.215, 0.352, 0.489, 0.626, 0.763, 0.900,
            0.037, 0.174, 0.311, 0.448, 0.585, 0.722, 0.859, 0.596, 0.133, 0.270, 0.407, 0.544, 0.681, 0.818, 0.955,
            0.092, 0.229, 0.366, 0.503, 0.640, 0.777, 0.914, 0.051, 0.188, 0.325, 0.462, 0.599, 0.736, 0.873, 0.610,
            0.147, 0.284, 0.421, 0.558, 0.695, 0.832, 0.969, 0.106, 0.243, 0.380, 0.517, 0.654, 0.791, 0.928, 0.065,
            0.202, 0.339, 0.476, 0.613, 0.750, 0.887, 0.024, 0.161, 0.298, 0.435, 0.572, 0.709, 0.846, 0.583, 0.120,
            0.257, 0.394, 0.531, 0.668, 0.805, 0.942, 0.079, 0.216, 0.353, 0.490, 0.627, 0.764, 0.901, 0.038, 0.175,
            0.312, 0.449, 0.586, 0.723, 0.860, 0.597, 0.134, 0.271, 0.408, 0.545, 0.682, 0.819, 0.956, 0.093, 0.230,
            0.367, 0.504, 0.641, 0.778, 0.915, 0.052, 0.189, 0.326, 0.463, 0.600, 0.737, 0.874, 0.611, 0.148, 0.285,
            0.422, 0.559, 0.696, 0.833, 0.970, 0.107, 0.244, 0.381, 0.518, 0.655, 0.792, 0.929, 0.066, 0.203, 0.340,
            0.477, 0.614, 0.751, 0.888, 0.025, 0.162, 0.299, 0.436, 0.573, 0.710, 0.847, 0.584, 0.121, 0.258, 0.395,
            0.532, 0.669, 0.806, 0.943, 0.080, 0.217, 0.354, 0.491, 0.628, 0.765, 0.902, 0.039, 0.176, 0.313, 0.450,
            0.587, 0.724, 0.861, 0.598, 0.135, 0.272, 0.409, 0.546, 0.683, 0.820, 0.957, 0.094, 0.231, 0.368, 0.505,
            0.642, 0.779, 0.916, 0.053, 0.190, 0.327, 0.464, 0.601, 0.738, 0.875, 0.612, 0.149, 0.286, 0.423, 0.560,
            0.697, 0.834, 0.971, 0.108, 0.245, 0.382, 0.519, 0.656, 0.793, 0.930, 0.067, 0.204, 0.341, 0.478, 0.615,
            0.752, 0.889, 0.026, 0.163, 0.300, 0.437, 0.574, 0.711, 0.848, 0.585, 0.122, 0.259, 0.396, 0.533, 0.670,
            0.807, 0.944, 0.081, 0.218, 0.355, 0.492, 0.629, 0.766, 0.903, 0.040, 0.177, 0.314, 0.451, 0.588, 0.725,
            0.862, 0.599, 0.136, 0.273, 0.410, 0.547, 0.684, 0.821, 0.958, 0.095, 0.232, 0.369, 0.506, 0.643, 0.780,
            0.917, 0.054, 0.191, 0.328, 0.465, 0.602, 0.739, 0.876, 0.613, 0.150, 0.287, 0.424, 0.561, 0.698, 0.835,
            0.572, 0.109, 0.246, 0.383, 0.520, 0.657, 0.794, 0.931, 0.068, 0.205, 0.342, 0.479, 0.616, 0.753, 0.890,
            0.027, 0.164, 0.301, 0.438, 0.575, 0.712, 0.849, 0.586, 0.123, 0.260, 0.397, 0.534, 0.671, 0.808, 0.945,
            0.082, 0.219, 0.356, 0.493, 0.630, 0.767, 0.904, 0.041, 0.178, 0.315, 0.452, 0.589, 0.726, 0.863, 0.600,
            0.137, 0.274, 0.411, 0.548, 0.685, 0.822, 0.959, 0.096, 0.233, 0.370, 0.507, 0.644, 0.781, 0.918, 0.055,
            0.192, 0.329, 0.466, 0.603, 0.740, 0.877, 0.614, 0.151, 0.288, 0.425, 0.562, 0.699, 0.836, 0.573, 0.110,
            0.247, 0.384, 0.521, 0.658, 0.795, 0.932, 0.069, 0.206, 0.343, 0.480, 0.617, 0.754, 0.891, 0.028, 0.165,
            0.302, 0.439, 0.576, 0.713, 0.850, 0.587, 0.124, 0.261, 0.398, 0.535, 0.672, 0.809, 0.946, 0.083, 0.220,
            0.357, 0.494, 0.631, 0.768, 0.905, 0.042, 0.179, 0.316, 0.453, 0.590, 0.727, 0.864, 0.601, 0.138, 0.275,
            0.412, 0.549, 0.686, 0.823, 0.960, 0.097, 0.234, 0.371, 0.508, 0.645, 0.782, 0.919, 0.056, 0.193, 0.330,
            0.467, 0.604, 0.741, 0.878, 0.615, 0.152, 0.289, 0.426, 0.563, 0.700, 0.837, 0.574, 0.111, 0.248, 0.385,
            0.522, 0.659, 0.796, 0.933, 0.070, 0.207, 0.344, 0.481, 0.618, 0.755, 0.892, 0.029, 0.166, 0.303, 0.440,
            0.577, 0.714, 0.851, 0.588, 0.125, 0.262, 0.399, 0.536, 0.673, 0.810, 0.947, 0.084, 0.221, 0.358, 0.495,
            0.632, 0.769, 0.906, 0.043, 0.180, 0.317, 0.454, 0.591, 0.728, 0.865, 0.602, 0.139, 0.276, 0.413, 0.550,
            0.687, 0.824, 0.961, 0.098, 0.235, 0.372, 0.509, 0.646, 0.783, 0.920, 0.057, 0.194, 0.331, 0.468, 0.605,
            0.742, 0.879, 0.616, 0.153, 0.290, 0.427, 0.564, 0.701, 0.838, 0.575, 0.112, 0.249, 0.386, 0.523, 0.660,
            0.797, 0.934, 0.071, 0.208, 0.345, 0.482, 0.619, 0.756, 0.893, 0.030, 0.167, 0.304, 0.441, 0.578, 0.715,
            0.852, 0.589, 0.126, 0.263, 0.400, 0.537, 0.674, 0.811, 0.948, 0.085, 0.222, 0.359, 0.496, 0.633, 0.770,
            0.907, 0.044, 0.181, 0.318, 0.455, 0.592, 0.729, 0.866, 0.603, 0.140, 0.277, 0.414, 0.551, 0.688, 0.825,
            0.962, 0.099, 0.236, 0.373, 0.510, 0.647, 0.784, 0.921, 0.058, 0.195, 0.332, 0.469, 0.606, 0.743, 0.880,
            0.617, 0.154, 0.291, 0.428, 0.565, 0.702, 0.839, 0.576, 0.113, 0.250, 0.387, 0.524, 0.661, 0.798, 0.935,
            0.072, 0.209, 0.346, 0.483, 0.620, 0.757, 0.894, 0.031, 0.168, 0.305, 0.442, 0.579, 0.716, 0.853, 0.590,
            0.127, 0.264, 0.401, 0.538, 0.675, 0.812, 0.949, 0.086, 0.223, 0.360, 0.497, 0.634, 0.771, 0.908, 0.045,
            0.182, 0.319, 0.456, 0.593, 0.730, 0.867, 0.604, 0.141, 0.278, 0.415, 0.552, 0.689, 0.826, 0.963, 0.100,
            0.237, 0.374, 0.511, 0.648, 0.785, 0.922, 0.059, 0.196, 0.333, 0.470, 0.607, 0.744, 0.881, 0.018, 0.155,
            0.292, 0.429, 0.566, 0.703, 0.840, 0.577, 0.114, 0.251, 0.388, 0.525, 0.662, 0.799, 0.936, 0.073, 0.210,
            0.347, 0.484, 0.621, 0.758, 0.895, 0.032, 0.169, 0.306, 0.443, 0.580, 0.717, 0.854, 0.591, 0.128, 0.265,
            0.402, 0.539, 0.676, 0.813, 0.950, 0.087, 0.224, 0.361, 0.498, 0.635, 0.772, 0.909, 0.046, 0.183, 0.320,
            0.457, 0.594, 0.731, 0.868, 0.605, 0.142, 0.279, 0.416, 0.553);

    static final String PATH = "/api/query/embeddings"; // NOSONAR

    static final String SELECT_ALL_QUERY = "SELECT * FROM SysContent";

    static final String ALT_USER_WITH_RESTRICTED_ACCESS = ALT_USER_ID;

    protected ResultActions checkResponse(Status expectedStatus, HttpMethod method, Map<String, Object> queryParameters)
            throws Exception {
        return checkResponse(expectedStatus, PATH, method, queryParameters);
    }

    @Test
    public void testEmbeddingQuery() throws Exception {
        Map<String, Object> postBodyData = Map.of("query", SELECT_ALL_QUERY, "vector", VECTOR, "limit", 1);
        var result = checkResponse(OK, POST, postBodyData);
        checkQueryResponseBody("rest/query_res_embedding.json", result);
    }

    @Test
    @WithUser(ALT_USER)
    public void testEmbeddingQueryWithAltUser() throws Exception {
        Map<String, Object> postBodyData = Map.of("query", SELECT_ALL_QUERY, "vector", VECTOR, "limit", 1);
        var result = checkResponse(OK, POST, postBodyData);
        checkQueryResponseBody("rest/query_res_embedding_alt_user.json", result);
    }

    @Test
    public void testEmbeddingQueryWithTotalCountAndPaging() throws Exception {
        List<Double> vector = IntStream.range(0, VECTOR_SIZE)
                                       .mapToObj(x -> Double.valueOf(x) / VECTOR_SIZE)
                                       .collect(toList());
        Map<String, Object> postBodyData = Map.of("query", SELECT_ALL_QUERY, "vector", vector, "trackTotalCount", true,
                "trackTotalCountUpTo", 97, "limit", 1, "offset", 33);
        var result = checkResponse(OK, POST, postBodyData);
        checkQueryResponseBody("rest/query_res_embedding_with_offset.json", result);
    }

    @Test
    public void testEmbeddingQueryWithEmbeddingType() throws Exception {
        String embeddingTypeWildcard = EMBEDDING_TYPE.substring(0, 3) + "*";
        Map<String, Object> postBodyData = Map.of("query", SELECT_ALL_QUERY, "embeddingType", embeddingTypeWildcard,
                "vector", VECTOR, "limit", 1);
        var result = checkResponse(OK, POST, postBodyData);
        checkQueryResponseBody("rest/query_res_embedding.json", result);
    }

    /** The document filter can be given as an expression, which the response echoes back as it was given. */
    @Test
    public void testEmbeddingQueryWithExprSyntax() throws Exception {
        Map<String, Object> postBodyData = Map.of("query", "content.sys_title != \"nosuchtitle\"", "syntax", "expr",
                "vector", VECTOR, "limit", 1);
        var result = checkResponse(OK, POST, postBodyData);
        checkQueryResponseBody("rest/query_res_embedding_expr.json", result);
        // the filter really applies to the documents the embeddings belong to
        postBodyData = Map.of("query", "content.sys_title == \"nosuchtitle\"", "syntax", "expr", "vector", VECTOR,
                "limit", 1);
        checkResponse(OK, POST, postBodyData).andExpect(jsonPath("count").value(0));
    }

    @Test
    public void testHybridQuery() throws Exception {
        Map<String, Object> postBodyData = Map.of("query", SELECT_ALL_QUERY, "vector", VECTOR, "limit", 1, "chunkFTS",
                "This is the text found in doc 8 and page 0 and a phrase elephant bear iguana");
        var result = checkResponse(OK, POST, postBodyData);
        checkQueryResponseBody("rest/query_res_embedding_hybrid.json", result);
    }
}
