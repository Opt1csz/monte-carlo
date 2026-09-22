import numpy as np
import matplotlib.pyplot as plt

Pop_size = 100
x = np.random.rand(Pop_size)

plt.ion()

fig, ax = plt.subplots()

for step in range(500):
    x += np.random.normal(0, 0.01, N)
    x = np.clip(x, 0, 1)

    ax.clear()
    ax.scatter(x, np.zeros(N), s=30)

    ax.set_xlim(0, 1)
    ax.set_ylim(-1, 1)
    ax.set_yticks([])
    ax.set_title(f"Step {step}")

    plt.pause(0.03)

plt.ioff()
plt.show()
