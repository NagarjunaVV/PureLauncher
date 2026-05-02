# AI Assistant Rules for PureLauncher

Welcome to the PureLauncher project! When assisting with this project, please adhere to the following workflow rules:

1. **Mandatory Context Loading**: Before beginning any new implementation, bug fix, or planning phase, you **MUST** read the `README.md` file located in the root directory. This provides the critical context regarding the project's architecture, roles (Child/Parent), and current status.
2. **Move to Implementation**: Once you have reviewed the `README.md`, you may proceed to analyzing the specific code files required for the task and generating your implementation plan or direct code edits.
3. **Android Version Targeting**: Always keep in mind that this application is targeting Android 13+ (Min SDK 33, Target SDK 36). Ensure that any code, especially regarding permissions (like Notifications or Package Usage Stats) or background services, complies with modern Android guidelines.
4. **Tech Stack**: Rely on Java, standard AndroidX components, and Firebase (Auth/Firestore) as defined in the build scripts.
