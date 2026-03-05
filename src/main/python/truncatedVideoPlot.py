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
    plt.title("Mean Scalability Results Over Two Runs")
    plt.grid(True)
    plt.legend()
    plt.show()

plot_results()

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

    plt.xlabel("Number of Frames")
    plt.ylabel("Time (s)")
    plt.title("Execution Time on Larger Instances")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.savefig("truncatedScalability.png")
    plt.show()

plot_results_500()

def analyze_complexity():
    matches = np.array([
        100,150,200,250,300,350,400,450,500,550,
        600,650,700,750,800,850,900,950,1000,1050,
        1100,1150,1200,1250,1300,1350,1400,1450,1500,
        2000,2500,3000,3500,4000,4500,5000,5500,6000,
        6500,7000,7500,8000,8500,9000,9500,10000,10500, 67500
    ])

    # Corrected times in seconds
    times = np.array([
        0.044,0.071,0.044,0.039,0.054,0.042,0.058,0.064,0.063,0.07,
        0.063,0.067,0.071,0.058,0.06,0.062,0.075,0.076,0.076,0.072,
        0.074,0.081,0.099,0.083,0.101,0.095,0.096,0.09,0.097,
        0.127,0.152,0.189,0.232,0.264,0.269,0.29,0.305,0.328,
        0.366,0.402,0.423,0.426,0.521,0.482,0.544,0.589,0.64, 35.568
    ])

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

    x_line = np.linspace(min(matches), max(matches), 300)

    # ---- Plot Linear ----
    plt.figure()
    plt.scatter(matches, times)
    plt.plot(x_line, linear_model(x_line))
    plt.xlabel("Matches")
    plt.ylabel("Time (s)")
    plt.title("Linear Fit")
    plt.grid(True)
    #plt.show()

    # ---- Plot Quadratic ----
    plt.figure()
    plt.scatter(matches, times)
    plt.plot(x_line, quad_model(x_line))
    plt.xlabel("Matches")
    plt.ylabel("Time (ms)")
    plt.title("Quadratic Fit")
    plt.grid(True)
    #plt.show()

    return quad_coeffs

#analyze_complexity()


def extrapolated_curve():

    quad_coeff = analyze_complexity()
    a = quad_coeff[0]
    b = quad_coeff[1]
    c = quad_coeff[2]
    print(a,b,c)

    # Original data
    matches = np.array([
        100,150,200,250,300,350,400,450,500,550,
        600,650,700,750,800,850,900,950,1000,1050,
        1100,1150,1200,1250,1300,1350,1400,1450,1500,
        2000,2500,3000,3500,4000,4500,5000,5500,6000,
        6500,7000,7500,8000,8500,9000,9500,10000,10500, 67500
    ])

    # Corrected times in seconds
    times = np.array([
        0.044,0.071,0.044,0.039,0.054,0.042,0.058,0.064,0.063,0.07,
        0.063,0.067,0.071,0.058,0.06,0.062,0.075,0.076,0.076,0.072,
        0.074,0.081,0.099,0.083,0.101,0.095,0.096,0.09,0.097,
        0.127,0.152,0.189,0.232,0.264,0.269,0.29,0.305,0.328,
        0.366,0.402,0.423,0.426,0.521,0.482,0.544,0.589,0.64, 35.568
    ])

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