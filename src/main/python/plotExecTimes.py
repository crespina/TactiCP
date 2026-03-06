import matplotlib.pyplot as plt

def plot_matches_time():
    matches = list(range(1, 91))
    times = [
        0.504, 0.191, 0.198, 0.234, 0.278, 0.396, 0.43, 0.379, 0.423, 0.464,
        0.526, 0.564, 0.613, 0.677, 0.69, 0.743, 0.786, 0.852, 0.905, 0.963,
        1.008, 1.156, 1.146, 1.17, 1.245, 1.257, 1.345, 1.398, 1.433, 1.507,
        1.541, 1.585, 1.681, 1.695, 1.719, 1.772, 1.839, 1.888, 1.941, 2.016,
        2.105, 2.101, 2.167, 2.237, 2.291, 2.362, 2.41, 2.415, 2.47, 2.521,
        2.548, 2.608, 2.66, 2.745, 2.794, 2.872, 2.911, 2.939, 2.997, 3.043,
        3.137, 3.129, 3.205, 3.293, 3.264, 3.411, 3.378, 3.518, 3.473, 3.49,
        3.626, 3.657, 3.672, 3.721, 3.713, 3.728, 3.728, 3.92, 3.945, 4.641,
        4.184, 4.069, 4.277, 4.266, 4.318, 4.522, 4.531, 5.064, 5.103, 4.824
    ]

    fig, ax = plt.subplots()
    ax.plot(matches, times, marker='o', color='blue', linestyle='')

    # Remove top and right spines
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)

    # Optional: remove the remaining spines for a fully frameless look
    # ax.spines['bottom'].set_visible(False)
    # ax.spines['left'].set_visible(False)

    ax.set_xlabel("Number of Matches")
    ax.set_ylabel("Time (s)")
    ax.grid(True, linestyle='--', alpha=0.5)
    plt.tight_layout()
    plt.savefig("./exectimes.png", transparent=True)  # transparent background
    plt.show()

plot_matches_time()

def plot_three_queries():
    matches = list(range(1, 91))

    # Times for each query
    simple_times = [
        0.404, 0.414, 0.537, 0.683, 0.808, 0.859, 0.972, 0.881, 1.172, 1.003,
        1.021, 1.166, 1.177, 1.667, 1.203, 1.44, 1.343, 1.502, 1.534, 1.808,
        1.737, 1.784, 2.036, 2.122, 2.322, 2.205, 2.334, 2.306, 2.611, 2.656,
        2.628, 2.911, 3.303, 3.321, 3.437, 3.285, 3.771, 3.523, 3.46, 3.953,
        3.701, 3.847, 4.073, 3.939, 4.195, 4.103, 4.403, 4.092, 4.835, 4.476,
        4.627, 4.502, 4.855, 4.691, 5.028, 4.882, 5.128, 5.054, 5.3, 5.184,
        5.448, 5.383, 5.5, 5.471, 5.859, 5.899, 5.966, 5.992, 6.128, 6.242,
        6.36, 6.43, 6.619, 6.629, 6.694, 6.764, 6.773, 6.887, 6.986, 7.104,
        7.207, 7.258, 7.314, 7.419, 7.5, 7.497, 7.6, 7.727, 7.747, 7.789
    ]

    middle_times = [
        0.093, 0.156, 0.484, 0.554, 0.692, 0.86, 0.646, 1.051, 1.171, 1.154,
        1.404, 1.558, 1.009, 1.13, 1.212, 1.347, 1.342, 1.521, 1.551, 1.591,
        1.627, 1.801, 2.045, 3.386, 3.502, 2.176, 2.174, 2.266, 2.402, 2.602,
        2.611, 2.673, 2.772, 3.001, 2.98, 3.07, 3.456, 3.164, 3.874, 3.542,
        3.482, 3.566, 3.906, 3.845, 4.168, 3.962, 4.257, 4.175, 4.361, 4.235,
        4.657, 4.483, 4.796, 4.55, 4.906, 4.829, 5.098, 5.061, 5.156, 5.143,
        5.324, 5.358, 5.586, 5.451, 5.706, 5.959, 6.035, 6.105, 6.18, 6.376,
        6.277, 6.41, 6.533, 6.579, 6.636, 6.683, 6.848, 6.888, 6.991, 6.995,
        7.086, 7.265, 7.36, 7.373, 7.54, 7.581, 7.659, 7.75, 7.807, 7.812
    ]

    hard_times = [
        0.083, 0.183, 0.241, 0.303, 0.385, 0.472, 0.528, 0.614, 0.651, 0.759,
        0.803, 0.896, 0.977, 1.08, 1.313, 1.269, 1.311, 1.363, 1.454, 1.555,
        1.714, 2.569, 1.826, 2.066, 2.07, 2.134, 2.15, 2.289, 2.593, 2.534,
        2.575, 2.688, 2.833, 2.899, 2.94, 3.136, 3.113, 3.154, 3.488, 3.289,
        3.469, 3.644, 3.65, 3.988, 3.787, 4.149, 3.978, 4.312, 4.23, 4.508,
        4.324, 4.52, 4.493, 4.734, 4.673, 4.997, 4.916, 5.139, 5.042, 5.205,
        5.339, 5.43, 5.763, 5.385, 5.696, 6.015, 6.01, 6.089, 6.119, 6.33,
        6.257, 6.402, 6.502, 6.581, 6.684, 6.699, 6.748, 6.92, 7.051, 7.111,
        7.068, 7.141, 7.272, 7.33, 7.535, 7.539, 7.602, 7.772, 7.781, 7.865
    ]

    fig, ax = plt.subplots()

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)
    
    # Plot each curve as scatter points
    ax.scatter(matches, simple_times, color='blue', label='Simple Query')
    ax.scatter(matches, middle_times, color='green', label='Intermediate Query')
    ax.scatter(matches, hard_times, color='red', label='Hard Query')

    # Remove top and right spines
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

    ax.set_xlabel("Number of Matches")
    ax.set_ylabel("Time (s)")
    ax.grid(True, linestyle='--', alpha=0.5)
    ax.legend(loc='lower right')
    plt.savefig("exectime3.png")
    plt.tight_layout()
    plt.show()

plot_three_queries()