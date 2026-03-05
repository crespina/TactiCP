import matplotlib.pyplot as plt

def plot_matches_time():
    matches = list(range(1, 51))
    times = [
        0.577, 0.204, 0.22, 0.286, 0.333, 0.453, 0.455, 0.505, 0.538, 0.548,
        0.622, 0.707, 0.719, 0.763, 0.847, 0.88, 0.956, 1.061, 1.068, 1.102,
        1.18, 1.228, 1.266, 1.289, 1.347, 1.397, 1.46, 1.472, 1.587, 1.583,
        1.636, 1.665, 1.779, 1.818, 1.825, 1.844, 1.856, 1.901, 1.955, 1.993,
        2.062, 2.088, 2.266, 2.224, 2.316, 2.406, 2.474, 2.461, 2.527, 2.616
    ]

    plt.figure()
    plt.plot(matches, times, marker='o')
    plt.xlabel("Number of Matches")
    plt.ylabel("Time (s)")
    plt.title("Execution Time vs Number of Matches")
    plt.grid(True)
    plt.tight_layout()
    plt.savefig("./exectimes.png")
    plt.show()

plot_matches_time()