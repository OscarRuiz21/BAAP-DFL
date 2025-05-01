
# Federated Learning on Hyperledger Fabric - Prototype v1

Este repositorio contiene un prototipo inicial para implementar un sistema de Aprendizaje Federado (FL) utilizando Hyperledger Fabric. Incluye el chaincode Java (`MyFLChaincode/FLContract`) y una aplicación cliente Java (`FLClient`) para simular el proceso.

Este README documenta los pasos para configurar, desplegar y probar el estado actual del proyecto utilizando la red de pruebas de Fabric.

## Prerrequisitos

Asegúrate de tener instalados los siguientes componentes:

* Docker y Docker Compose
* Git
* Go (Necesario para Fabric)
* Java JDK 17 (o superior, pero configurado para usar la 17 para el chaincode)
* Gradle (Usualmente incluido con el proyecto o descargado por el wrapper)
* Hyperledger Fabric Samples (Clonado en tu sistema, asumiremos que está en un directorio paralelo a este proyecto)

## Estructura Asumida de Directorios

<tu_directorio_base>/
├── fabric-samples/       # Clon de Hyperledger Fabric Samples
│   └── test-network/
├── MyFLChaincode/        # Este proyecto (Chaincode Java)
│   └── ... (código fuente, build.gradle, etc.)
└── FLClient/             # Proyecto del Cliente Java (creado separadamente)
└── ... (código fuente, build.gradle, etc.)


*(Nota: Si tu estructura es diferente, ajusta las rutas `cd ../../` en los comandos)*

## I. Configuración y Despliegue del Chaincode

Sigue estos pasos desde una terminal.

**1. Configurar Entorno Java (Ejemplo para macOS)**

Asegúrate de que `JAVA_HOME` apunte a tu JDK 17 y esté en el `PATH`.

```bash
# (macOS) Seleccionar JDK 17 instalada (ajusta según tu instalación)
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
# O establece la ruta manualmente si es necesario:
# export JAVA_HOME="/Library/Java/JavaVirtualMachines/openjdk-17.0.2/Contents/Home"

# Añadir Java al PATH
export PATH="$JAVA_HOME/bin:$PATH"

# Verificar la versión
echo $JAVA_HOME
java -version
2. Compilar el Chaincode

Navega al directorio del chaincode y usa Gradle para compilarlo.

Bash

# Asegúrate de estar en tu directorio base
# cd <tu_directorio_base>

cd MyFLChaincode
./gradlew clean installDist # Debe terminar con BUILD SUCCESSFUL
3. Levantar la Red de Pruebas de Fabric

Ve al directorio de la red de pruebas y levántala con CouchDB habilitado.

Bash

# Si estás en MyFLChaincode, sube dos niveles y entra a fabric-samples
cd ../../fabric-samples/test-network
# Si estás en tu directorio base, entra directamente
# cd fabric-samples/test-network

# Levanta la red con CAs y CouchDB, crea el canal 'mychannel'
./network.sh up createChannel -ca -s couchdb
Nota: Si la red ya está corriendo, puedes detenerla primero con ./network.sh down.

4. Desplegar el Chaincode en la Red

Desde el directorio test-network, despliega el chaincode compilado.

Bash

# Aún dentro de fabric-samples/test-network

./network.sh deployCC \
  -ccn flcontract \          # Nombre lógico del chaincode
  -ccl java \                # Lenguaje del chaincode
  -ccv 1.0 \                 # Versión
  -ccs 1 \                   # Secuencia (incrementar si actualizas)
  -ccp ../../MyFLChaincode   # Ruta RELATIVA al proyecto del chaincode
Este script hará lo siguiente:

Recompilará el chaincode (por si acaso).
Empaquetará el chaincode en flcontract.tar.gz.
Instalará el paquete en los peers peer0.org1.example.com y peer0.org2.example.com.
Aprobará la definición del chaincode para Org1 y Org2.
Hará commit de la definición en el canal mychannel.
II. Interacción con el Chaincode (Usando peer CLI)
Estos comandos se ejecutan desde el directorio fabric-samples/test-network.

1. Establecer Variables de Entorno Comunes (Certificados)

Bash

# Aún dentro de fabric-samples/test-network

export ORDERER_CA=<span class="math-inline">\{PWD\}/organizations/ordererOrganizations/\[example\.com/tlsca/tlsca\.example\.com\-cert\.pem\]\(https\://<0\>example\.com/tlsca/tlsca\.example\.com\-cert\.pem\)
export PEER0\_ORG1\_CA\=</span>{PWD}/organizations/peerOrganizations/[org1.example.com/tlsca/tlsca.org1.example.com-cert.pem](https://org1.example.com/tlsca/tlsca.org1.example.com-cert.pem)
export PEER0_ORG2_CA=${PWD}/organizations/peerOrganizations/[org2.example.com/tlsca/tlsca.org2.example.com-cert.pem](https://org2.example.com/tlsca/tlsca.org2.example.com-cert.pem)

# Asegúrate de estar en el directorio correcto (fabric-samples/test-network)
echo "Current directory: $PWD"
2. Interactuar como Admin de Org1

Establece las variables para actuar como el administrador de Org1.

Bash

export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=<span class="math-inline">PEER0\_ORG1\_CA
export CORE\_PEER\_MSPCONFIGPATH\=</span>{PWD}/organizations/peerOrganizations/[org1.example.com/users/Admin@org1.example.com/msp](https://org1.example.com/users/Admin@org1.example.com/msp)
export CORE_PEER_ADDRESS=localhost:7051 # Dirección de peer0.org1
3. Enviar Actualizaciones de Modelo (Ronda 1)

Envía propuestas a ambos peers para asegurar que la transacción sea validada por las políticas.

Actualización Nodo 1:

Bash

peer chaincode invoke \
  -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com \
  --tls --cafile $ORDERER_CA \
  -C mychannel \
  -n flcontract \
  --waitForEvent \
  --peerAddresses localhost:7051 --tlsRootCertFiles $PEER0_ORG1_CA \
  --peerAddresses localhost:9051 --tlsRootCertFiles $PEER0_ORG2_CA \
  -c '{"Args":["FLContract:submitModelUpdate","Node1","1","{\"nodeId\":\"Node1\",\"roundNumber\":\"1\",\"weights\":[0.1,0.2,0.3]}"]}'
Actualización Nodo 2:

Bash

peer chaincode invoke \
  -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com \
  --tls --cafile $ORDERER_CA \
  -C mychannel \
  -n flcontract \
  --waitForEvent \
  --peerAddresses localhost:7051 --tlsRootCertFiles $PEER0_ORG1_CA \
  --peerAddresses localhost:9051 --tlsRootCertFiles $PEER0_ORG2_CA \
  -c '{"Args":["FLContract:submitModelUpdate","Node2","1","{\"nodeId\":\"Node2\",\"roundNumber\":\"1\",\"weights\":[0.2,0.3,0.4]}"]}'
4. Realizar la Agregación (Ronda 1)

Invoca la función para agregar las actualizaciones de la ronda 1, esperando 2 actualizaciones.

Bash

peer chaincode invoke \
  -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com \
  --tls --cafile $ORDERER_CA \
  -C mychannel \
  -n flcontract \
  --waitForEvent \
  --peerAddresses localhost:7051 --tlsRootCertFiles $PEER0_ORG1_CA \
  --peerAddresses localhost:9051 --tlsRootCertFiles $PEER0_ORG2_CA \
  -c '{"Args":["FLContract:performAggregation","1","2"]}'
5. Consultar el Modelo Agregado (Ronda 1)

Consulta el resultado de la agregación.

Bash

# Como Org1 (ya configurado)
peer chaincode query \
  -C mychannel \
  -n flcontract \
  -c '{"Args":["FLContract:getAggregatedModel","1"]}' | jq # Usa jq para formatear el JSON si lo tienes instalado
6. Interactuar como Admin de Org2 (Opcional)

Para enviar transacciones o consultas desde la perspectiva de Org2, cambia las variables de entorno:

Bash

export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=<span class="math-inline">PEER0\_ORG2\_CA
export CORE\_PEER\_MSPCONFIGPATH\=</span>{PWD}/organizations/peerOrganizations/[org2.example.com/users/Admin@org2.example.com/msp](https://org2.example.com/users/Admin@org2.example.com/msp)
export CORE_PEER_ADDRESS=localhost:9051 # Dirección de peer0.org2

# Ahora puedes ejecutar comandos 'peer chaincode invoke/query' como Org2
# Ejemplo: consultar el modelo agregado desde Org2
peer chaincode query \
  -C mychannel \
  -n flcontract \
  -c '{"Args":["FLContract:getAggregatedModel","1"]}' | jq
Recuerda volver a establecer las variables de Org1 si quieres seguir interactuando como Org1.

III. Ejecución de la Aplicación Cliente (FLClient)
Estos pasos asumen que tienes un proyecto Java separado para el cliente (FLClient) y estás en el directorio raíz de ese proyecto.

1. Crear Directorios Necesarios

Dentro del directorio FLClient/:

Bash

# Estando en el directorio FLClient/
mkdir -p wallet tls msp/signcerts msp/keystore
2. Copiar Recursos de la Red

Copia los archivos necesarios desde la red de pruebas de Fabric al directorio FLClient/. Asegúrate de ajustar la ruta ../../fabric-samples/test-network si tu estructura es diferente.

Archivo de Conexión (Connection Profile):

Bash

# Ajusta la ruta si es necesario
cp ../../fabric-samples/test-network/organizations/peerOrganizations/[org1.example.com/connection-org1.yaml](https://org1.example.com/connection-org1.yaml) connection.yaml
Certificado TLS del Peer de Org1:

Bash

# Ajusta la ruta si es necesario
cp ../../fabric-samples/test-network/organizations/peerOrganizations/[org1.example.com/tlsca/tlsca.org1.example.com-cert.pem](https://org1.example.com/tlsca/tlsca.org1.example.com-cert.pem) tls/peer0-org1-cert.pem
Identidad de Admin de Org1 (Certificado y Clave Privada):

Bash

# Certificado (Ajusta la ruta si es necesario)
cp ../../fabric-samples/test-network/organizations/peerOrganizations/[org1.example.com/users/Admin@org1.example.com/msp/signcerts/*.pem](https://org1.example.com/users/Admin@org1.example.com/msp/signcerts/*.pem) msp/signcerts/cert.pem

# Clave privada (Ajusta la ruta si es necesario, el nombre del archivo puede variar, *_sk lo captura)
cp ../../fabric-samples/test-network/organizations/peerOrganizations/[org1.example.com/users/Admin@org1.example.com/msp/keystore/*_sk](https://org1.example.com/users/Admin@org1.example.com/msp/keystore/*_sk) msp/keystore/priv_sk
3. Ejecutar el Cliente Java

Compila y ejecuta la aplicación cliente usando Gradle.

Bash

# Estando en el directorio FLClient/
./gradlew clean run
El cliente simulará 3 rondas de FL, enviando actualizaciones dummy, realizando la agregación y consultando el resultado.

IV. Verificación
Puedes monitorizar las transacciones y logs del chaincode directamente desde los contenedores Docker de los peers.

1. Ver Logs del Peer (Ejemplo para Org1)

Abre otra terminal y ejecuta:

Bash

# Asegúrate de que los contenedores de la red de pruebas estén corriendo
docker logs -f peer0.org1.example.com 2>&1 | grep FLContract
Esto mostrará en tiempo real los mensajes (System.out.printf) generados por tu chaincode FLContract cuando se ejecutan sus funciones. Puedes hacer lo mismo para peer0.org2.example.com.

Limpieza
Para detener la red de pruebas de Fabric:

Bash

# Navega a fabric-samples/test-network si no estás ahí
# cd <ruta_a_tu_directorio_base>/fabric-samples/test-network

# Detiene y elimina los contenedores, volúmenes y redes
./network.sh down
