Super Ten Challenge!

A procedurally generated 3D maze exploration game focused on structured navigation, thorough traversal, and player discipline.

🎮 Game Description

Super Ten Challenge! is a first-person maze game where each level is a fully generated, finite, and connected labyrinth. The objective is simple:

Collect all dots, then find and reach the exit.

What makes it challenging is how the maze must be explored.

Mazes increase in size and complexity across levels
Every corridor matters — missed paths can come back later
There are no loops, ensuring the maze is always solvable
A wall-following strategy can always guarantee eventual completion

The game rewards careful, structured exploration over speed, pushing the player to stay disciplined and methodical throughout long sessions.

🧠 Core Gameplay Principles
Cleaner, not faster
Fully explore corridors when you enter them
Avoid assumption-based movement
Use wall-following as a guaranteed fallback
🗝️ Features
Procedurally generated maze system
Multiple levels with increasing scale
Colored keys and locked doors
Skeleton key system (limited-use universal keys)
Pressure plates and dynamic barriers
Warp system for partial progression recovery
Region-based visual theming for navigation
Real-time software rendering (no external engine)
🎯 Objective
Explore the maze
Collect all dots
Unlock paths as needed
Reach the exit

⚠️ The exit remains locked until all dots are collected.

🎮 Controls
Action	Key
Move Forward	I
Move Back	K
Strafe Left	N
Strafe Right	M
Turn Left	J
Turn Right	L
Interact	E
Reset Level	SPACE
Toggle HUD	H
Toggle Inventory	V
▶️ Entry Point

The game starts from:

Main.java

⚠️ Notes for Players
Thorough exploration is essential — missed paths can become problems later
The game emphasizes control and consistency over speed
Late-game scenarios can become mentally demanding if earlier exploration was incomplete
🛠️ Technical Overview
Custom Java software renderer (Z-buffer + clipping + BSP)
Procedural maze generation with guaranteed solvability
Dynamic scene rebuilding for interactive elements
Region-based texture and lighting system
🚀 Status

✔ Completed and ready for release
✔ Tested through large-scale maze levels
✔ Core systems validated

📌 Final Thought

This is not a game about rushing.

It’s about maintaining control in a system where every decision compounds over time.
