# Integración continua

El flujo de trabajo `CI` de GitHub Actions compila y prueba todos los servicios después de cada
push y en cada pull request dirigido a `main`.

Los cuatro servicios se ejecutan de forma independiente y en paralelo. La comprobación final
`CI quality gate` solo termina correctamente cuando todos ellos han finalizado con éxito.
Cuando una compilación falla, sus informes de pruebas de Gradle se adjuntan a la ejecución del
flujo de trabajo durante siete días.

## Protección de `main`

El flujo de trabajo comunica el resultado, pero una regla del repositorio de GitHub debe convertir ese
resultado en un requisito previo a la fusión. Después de que el flujo se haya ejecutado en GitHub
al menos una vez:

1. Abra **Settings > Rules > Rulesets** en el repositorio de GitHub.
2. Cree un conjunto de reglas para ramas en modo de aplicación **Active** que se dirija a la rama
   predeterminada (`main`).
3. Active la opción que exige una solicitud de cambios antes de fusionar.
4. Active la opción que exige superar las comprobaciones de estado y añada `CI quality gate` como
   comprobación obligatoria, con GitHub Actions como origen.
5. Active la opción que exige que las ramas estén actualizadas antes de fusionar si cada solicitud
   de cambios también debe probarse con la versión más reciente de `main`.
6. No añada administradores ni roles del repositorio a la lista de excepciones si la regla debe
   aplicarse a todo el mundo.

Con ese conjunto de reglas activo, un `CI quality gate` fallido o pendiente impide fusionar
el pull request en `main`. Los commits directos deben realizarse en una rama de funcionalidad y
fusionarse mediante un pull request.

## Ejecución local de las mismas comprobaciones

En Linux o macOS, ejecute este comando una vez desde el directorio de cada servicio:

```shell
./gradlew clean build
```

En Windows, ejecute:

```powershell
.\gradlew.bat clean build
```
