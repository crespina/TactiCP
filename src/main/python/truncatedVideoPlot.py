import matplotlib.pyplot as plt
import numpy as np

def plot_results():

    # Matches (same for both runs)
    matches = np.array([
        100,150,200,250,300,350,400,450,500,550,
        600,650,700,750,800,850,900,950,1000,1050,
        1100,1150,1200,1250,1300,1350,1400,1450,1500,
        2000,2500,3000,3500,4000,4500,5000,5500,6000,
        6500,7000,7500,8000,8500,9000,9500,10000,10500
    ])

    # Corrected times in seconds
    times = np.array([
        0.044,0.071,0.044,0.039,0.054,0.042,0.058,0.064,0.063,0.07,
        0.063,0.067,0.071,0.058,0.06,0.062,0.075,0.076,0.076,0.072,
        0.074,0.081,0.099,0.083,0.101,0.095,0.096,0.09,0.097,
        0.127,0.152,0.189,0.232,0.264,0.269,0.29,0.305,0.328,
        0.366,0.402,0.423,0.426,0.521,0.482,0.544,0.589,0.64
    ])

    fig, ax = plt.subplots()
    ax.plot(matches, times, marker='o', color='blue', linestyle='')

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)

    plt.xlabel("Number of Matches")
    plt.ylabel("Time (s)")
    plt.grid(True)
    plt.legend()
    plt.show()

#plot_results()

def plot_results_500():

    # Matches (same for both runs)
    matches = np.array([
        100,500,
        1000,
        1500,
        2000,2500,3000,3500,4000,4500,5000,5500,6000,
        6500,7000,7500,8000,8500,9000,9500,10000,10500
    ])

    # Corrected times in seconds
    times = np.array([
        0.044,0.063,
        0.076,
        0.097,
        0.127,0.152,0.189,0.232,0.264,0.269,0.29,0.305,0.328,
        0.366,0.402,0.423,0.426,0.521,0.482,0.544,0.589,0.64
    ])

    fig, ax = plt.subplots()
    ax.plot(matches, times, marker='o', color='blue', linestyle='')

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)

    ax.set_xlabel("Number of Frames")
    ax.set_ylabel("Time (s)")
    ax.grid(True, linestyle='--', alpha=0.5)
    plt.legend()
    plt.tight_layout()
    plt.savefig("truncatedScalability.png")
    plt.show()

#plot_results_500()

def analyze_complexity(matches, times):

    # ---- Linear fit ----
    linear_coeffs = np.polyfit(matches, times, 1)
    linear_model = np.poly1d(linear_coeffs)
    linear_pred = linear_model(matches)

    # R² linear
    ss_res_lin = np.sum((times - linear_pred)**2)
    ss_tot = np.sum((times - np.mean(times))**2)
    r2_linear = 1 - ss_res_lin/ss_tot

    # ---- Quadratic fit ----
    quad_coeffs = np.polyfit(matches, times, 2)
    quad_model = np.poly1d(quad_coeffs)
    quad_pred = quad_model(matches)

    # R² quadratic
    ss_res_quad = np.sum((times - quad_pred)**2)
    r2_quad = 1 - ss_res_quad/ss_tot

    print("R² Linear:", r2_linear)
    print("R² Quadratic:", r2_quad)

    return quad_coeffs


def extrapolated_curve(matches, times):

    quad_coeff = analyze_complexity(matches, times)
    a = quad_coeff[0]
    b = quad_coeff[1]
    c = quad_coeff[2]
    print(a,b,c)

    # Extrapolated curve
    x_line = np.linspace(0, 70000, 500)
    y_line = a*x_line**2 + b*x_line + c

    plt.figure(figsize=(10,6))
    plt.plot(x_line, y_line, label="Quadratic Fit (extrapolated)")
    plt.scatter(matches, times, color='red', label="Original Data")
    plt.xlabel("Matches")
    plt.ylabel("Time (s)")
    plt.title("Extrapolated Runtime up to 70,000 Matches")
    plt.grid(True)
    plt.legend()
    plt.show()

#extrapolated_curve()




import matplotlib.pyplot as plt

def plot_large_queries():
    matches = [
        50, 100, 150, 200, 250, 300, 350, 400, 450, 500,
        550, 600, 650, 700, 750, 800, 850, 900, 950, 1000,
        1050, 1100, 1150, 1200, 1250, 1300, 1350, 1400, 1450, 1500,
        2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500,
        7000, 7500, 8000, 8500, 9000, 9500, 10000, 10500
    ]

    simple_times = [
        0.059, 0.089, 0.093, 0.098, 0.111, 0.112, 0.115, 0.216, 0.127, 0.143,
        0.148, 0.161, 0.182, 0.174, 0.189, 0.256, 0.244, 0.243, 0.231, 0.267,
        0.239, 0.224, 0.235, 0.239, 0.268, 0.252, 0.277, 0.32, 0.333, 0.31,
        0.411, 0.488, 0.348, 0.552, 0.432, 0.411, 0.942, 0.589, 0.481, 0.56,
        0.623, 0.652, 0.684, 0.779, 1.228, 0.761, 0.79, 0.843
    ]

    intermediate_times = [
        0.047, 0.039, 0.05, 0.058, 0.064, 0.073, 0.083, 0.092, 0.106, 0.12,
        0.121, 0.127, 0.135, 0.149, 0.152, 0.169, 0.142, 0.191, 0.198, 0.171,
        0.203, 0.214, 0.221, 0.225, 0.234, 0.247, 0.179, 0.268, 0.273, 0.284,
        0.337, 0.272, 0.336, 0.281, 0.338, 0.339, 0.375, 0.41, 0.49, 0.463,
        0.517, 0.571, 0.536, 0.594, 0.651, 0.692, 0.747, 0.764
    ]

    hard_times = [
        0.029, 0.03, 0.027, 0.043, 0.066, 0.076, 0.076, 0.088, 0.094, 0.106,
        0.109, 0.127, 0.125, 0.133, 0.144, 0.152, 0.163, 0.171, 0.176, 0.194,
        0.201, 0.216, 0.217, 0.209, 0.114, 0.127, 0.123, 0.137, 0.144, 0.146,
        0.168, 0.203, 0.228, 0.303, 0.284, 0.313, 0.347, 0.389, 0.391, 0.42,
        0.442, 0.487, 0.508, 0.578, 0.577, 0.624, 0.666, 0.704
    ]

    fig, ax = plt.subplots(figsize=(8,6))

    ax.scatter(matches, simple_times, color='blue', label='Simple Query')
    ax.scatter(matches, intermediate_times, color='green', label='Intermediate Query')
    ax.scatter(matches, hard_times, color='red', label='Hard Query')

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)

    ax.set_xlabel("Number of Matches")
    ax.set_ylabel("Time (s)")
    #ax.set_xscale('log')  # optional, makes big match numbers easier to read
    #ax.set_yscale('log')  # optional, helps with extreme values
    ax.grid(True, linestyle='--', alpha=0.5)

    # Legend to the right below the plot
    ax.legend(loc='lower right')

    plt.tight_layout()
    plt.savefig("trunc3.png")
    plt.show()

#plot_large_queries()

def plot_large_queries500():
    matches = [
        50, 500,
        1000,
        1500,
        2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500,
        7000, 7500, 8000, 8500, 9000, 9500, 10000, 10500
    ]

    simple_times = [
        0.059, 0.143,
        0.267,
        0.31,
        0.411, 0.488, 0.348, 0.552, 0.432, 0.411, 0.942, 0.589, 0.481, 0.56,
        0.623, 0.652, 0.684, 0.779, 1.228, 0.761, 0.79, 0.843
    ]

    intermediate_times = [
        0.047, 0.12,
        0.171,
        0.284,
        0.337, 0.272, 0.336, 0.281, 0.338, 0.339, 0.375, 0.41, 0.49, 0.463,
        0.517, 0.571, 0.536, 0.594, 0.651, 0.692, 0.747, 0.764
    ]

    hard_times = [
        0.029, 0.106,
        0.194,
        0.146,
        0.168, 0.203, 0.228, 0.303, 0.284, 0.313, 0.347, 0.389, 0.391, 0.42,
        0.442, 0.487, 0.508, 0.578, 0.577, 0.624, 0.666, 0.704
    ]

    fig, ax = plt.subplots(figsize=(8,6))

    ax.scatter(matches, simple_times, color='blue', label='Simple Query')
    ax.scatter(matches, intermediate_times, color='green', label='Intermediate Query')
    ax.scatter(matches, hard_times, color='red', label='Hard Query')

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    ax.spines['bottom'].set_visible(False)
    ax.spines['left'].set_visible(False)

    ax.set_xlabel("Number of Matches")
    ax.set_ylabel("Time (s)")
    #ax.set_xscale('log')  # optional, makes big match numbers easier to read
    #ax.set_yscale('log')  # optional, helps with extreme values
    ax.grid(True, linestyle='--', alpha=0.5)

    # Legend to the right below the plot
    ax.legend(loc='lower right')

    plt.tight_layout()
    plt.savefig("trunc3.png")
    plt.show()

#plot_large_queries500()

def barplot67():
    labels = ["Simple", "Intermediate", "Hard"]
    times = [6.202, 48.151, 1119.826]
    colors = ["blue", "green", "red"]

    fig, ax = plt.subplots()

    ax.bar(labels, times, color=colors)

    ax.set_ylabel("Time (s)")

    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)

    for i, v in enumerate(times):
        ax.text(i, v, f"{v:.2f}", ha='center', va='bottom')

    plt.yscale("log")

    plt.tight_layout()
    plt.savefig("barplot67.png")
    plt.show()

#barplot67()


matches = [
    50, 500,
    1000,
    1500,
    2000, 2500, 3000, 3500, 4000, 4500, 5000, 5500, 6000, 6500,
    7000, 7500, 8000, 8500, 9000, 9500, 10000, 10500, 67500
]

simple_times = [
    0.059, 0.143,
    0.267,
    0.31,
    0.411, 0.488, 0.348, 0.552, 0.432, 0.411, 0.942, 0.589, 0.481, 0.56,
    0.623, 0.652, 0.684, 0.779, 1.228, 0.761, 0.79, 0.843, 6.202
]

intermediate_times = [
    0.047, 0.12,
    0.171,
    0.284,
    0.337, 0.272, 0.336, 0.281, 0.338, 0.339, 0.375, 0.41, 0.49, 0.463,
    0.517, 0.571, 0.536, 0.594, 0.651, 0.692, 0.747, 0.764, 48.151
]

hard_times = [
    0.029, 0.106,
    0.194,
    0.146,
    0.168, 0.203, 0.228, 0.303, 0.284, 0.313, 0.347, 0.389, 0.391, 0.42,
    0.442, 0.487, 0.508, 0.578, 0.577, 0.624, 0.666, 0.704, 1119.826
]


extrapolated_curve(matches, hard_times)
