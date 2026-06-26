## 1. Architecture Components
We are following the clean architecture for this project. All the code for the client app is inside the `client-app/track-my-buds` folder. The folders mentioned below are inside the `client-app/track-my-buds` folder.

Whenever generating the code, we will be using the code structure as mentioned below in the document.

#### Domain Layer
- Contains the code and interfaces related to core business logic.
- `lib/domain` folder
- Inside the `domain` folder, we have the following subfolders:
    - `entities` folder listing the core entities.
    - `repositories` folder listing the repository interfaces.
    - `usecase` folder listing the usecases for the business logic.

#### Data Layer
- `lib/data` folder
- Inside the `data` folder, we have the following subfolders:
    - `datasources` folder listing the data source interfaces.
- Each repository has a corresponding datasource interface int the file with the format `<repo_name>_datasource.dart`.
- The datasource interfaces have exacly the same methods as the corresponding repository unless there are any specific mentions.
    
#### Implementation layer
- This folder contains implementations for the domain and data layer implementation.
- `lib/implementation` folder
- Inside the `implementation` folder, we have the following subfolders:
    - `data` folder listing the data layer implementations. Inside this, we have the following subfolders.
      - `mapper` folder listing the mapper implementations for converting the datasource specfic models to the domain entities.
      - `datasource` folder listing the data sources implementations. Inside the datasource folder, we have the following subfolders:
        - `local` folder listing the local data sources implementations.
        - `remote` folder listing the remote data sources implementations.
        - `mock` folder listing the mock data sources implementations.
    - `domain` folder listing the domain layer implementations.

#### Presentation Layer
- This folder contains the code and interfaces related to the UI.
- `lib/presentation` folder
- Inside the `presentation` folder, we have the following subfolders:
    - `screen` folder listing different screens of the app. Each screen will have it's own nested folder. eg. `lib/presentation/screen/home_screen` for the home screen of the app. Each screen directory will have the following files:
        - `<screen-name>_screen.dart` file listing the screen widget.
        - `<screen-name>_bloc.dart` file listing the screen bloc.
        - `<screen-name>_bloc_event.dart` file listing the screen bloc events.
        - `<screen-name>_bloc_state.dart` file listing the screen bloc states.
    - `widgets` folder listing the widgets used in the app.
    - `navigation` folder listing the navigation logic and utilities.
    - `theme` folder listing the theme related code.
    - `utils` folder listing the presentation layer utilities.
- `main.dart` file listing the entry point of the app. This file will inject instance for the router and redirect to the home screen.

## Dependency injection (di)
- We will use Getit for dependency injection.
- When setting up please add a dependency for Getit.
- The code related to dependency injection will be present in the folder `lib/di`
- Master file for di is `injection.dart`


## 2. Project Structure

```
Structure inside the lib folder.
│   main.dart
│
├───data
│   └───datasources
├───di
│       injection.dart
│
├───domain
│   ├───entity
│   ├───repository
│   └───usecase
│
├───implementation
│   ├───data
│   │   └───datasource
│   │       ├───local
│   │       ├───mock
│   │       └───remote
│   └───domain
│       └───repository
```

## 3. Setup - Run below steps when asked to run the basic setup.
- Add Getit dependency injection in `pubsec.yaml`.
- Add Mockito dependency in `pubsec.yaml`.
- Install Build Runner dependency in `pubsec.yaml`
- Create the folder structure as mentioned above.
- Setup the basic files for dependency injection with code for dependency validator.