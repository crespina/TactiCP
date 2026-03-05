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