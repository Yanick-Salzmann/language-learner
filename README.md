# AI Language Learner

A modern web application that provides AI-powered language learning through conversational chat. Users can practice languages by chatting with an AI teacher that provides corrections, suggestions, and structured learning guidance.

## 🚀 Features

- **AI-Powered Conversations**: Chat with an AI language teacher using LangChain4j
- **Multiple Language Support**: Practice English, Spanish, French, German, Italian, and Portuguese (more languages can be added)
- **Real-time Streaming**: Get AI responses in real-time with Server-Sent Events
- **Chat History**: Persistent chat sessions with PostgreSQL database storage
- **Text-to-Speech**: Audio playback for AI responses to help with pronunciation
- **Modern UI**: Responsive Angular frontend with SIX Group UI components
- **Multiple AI Providers**: Support for Ollama, OpenAI, and Azure AI models

## 🛠️ Technology Stack

### Backend
- **Kotlin** with Spring Boot 3.4.5
- **Spring WebFlux** for reactive programming
- **LangChain4j** for AI integration
- **PostgreSQL** for chat history persistence
- **WebSockets** for real-time communication

### Frontend
- **Angular 18** with TypeScript
- **SIX Group UI Library** for modern components
- **RxJS** for reactive programming
- **ngx-markdown** for message formatting

### AI Integration
- **Ollama** (default) - Local AI models
- **OpenAI** - GPT models
- **Azure AI** - Azure OpenAI Service

## 📋 Requirements

- **Java 17** or higher
- **Maven 3.6** or higher
- **PostgreSQL** database server
- **Node.js 20.11.0** (automatically installed via frontend-maven-plugin)
- **AI Provider** (Ollama, OpenAI API key, or Azure credentials)
- **CUDA Toolkit** (need for GPU acceleration of local AI based text to speech)
  - Required for optimal performance when using Ollama with GPU-accelerated models
  - Download from [NVIDIA CUDA Toolkit](https://developer.nvidia.com/cuda-toolkit)
  - Ensure your NVIDIA GPU supports CUDA and has sufficient VRAM (8GB+ recommended)

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone <repository-url>
cd ai-language-learner
```

### 2. Setup Database

Install and configure PostgreSQL:

1. **Install PostgreSQL** (version 12 or higher)
2. **Create a database** (default: `postgres`)
3. **Configure connection** in `src/main/resources/application-local.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    driverClassName: org.postgresql.Driver
    username: postgres
    password: your-password
```

### 3. Configure AI Provider

Edit `src/main/resources/application.yml`:

**For Ollama (default):**
```yaml
ai:
  provider: ollama
  ollama:
    base-url: http://localhost:11434  # Default Ollama URL
    model: deepseek-r1                # Or any other model you have
```

**For OpenAI:**
```yaml
ai:
  provider: openai
  openai:
    api-key: your-openai-api-key
    model: gpt-3.5-turbo              # Or gpt-4, etc.
```

**For Azure:**
```yaml
ai:
  provider: azure
  azure:
    api-key: your-azure-api-key
    endpoint: your-azure-endpoint
    deployment-name: your-deployment-name
```

### 4. Run the Application

**Using Maven:**
```bash
# Build and run the application (includes frontend build)
mvn clean spring-boot:run -Dspring-boot.run.profiles=local
```

**Using JAR:**
```bash
# Build the complete application
mvn clean package

# Run the JAR with local profile
java -jar target/ai-language-learner-1.0-SNAPSHOT.jar --spring.profiles.active=local
```

The application will start on `http://localhost:8080`

## 🎯 How to Use

1. **Open the application** in your browser at `http://localhost:8080`
2. **Select a language** from the dropdown in the sidebar
3. **Click "New Chat"** to start a conversation
4. **Type your message** in the target language and press Enter
5. **Receive AI feedback** with corrections and suggestions
6. **Click the speaker icon** on AI messages to hear pronunciation
7. **View chat history** in the sidebar to continue previous conversations

## 🔧 Development

### Frontend Development
The Angular frontend is located in `src/main/frontend/`. For development:

```bash
cd src/main/frontend
npm install
npm start  # Runs on http://localhost:4200
```

### Backend Development
The Kotlin backend uses Spring Boot with hot reload support:

```bash
mvn spring-boot:run
```

### Running Tests
```bash
# Run all tests
mvn test

# Run only backend tests
mvn test -DskipFrontend

# Run frontend tests
cd src/main/frontend
npm test
```

## 📊 API Endpoints

- `GET /api/chat/sessions` - Get all chat sessions
- `POST /api/chat/sessions` - Create new chat session
- `GET /api/chat/sessions/{id}/messages` - Get chat history
- `GET /api/chat/sessions/{id}/messages/new` (SSE) - Send message and receive streaming response - uses GET due to SSE limitations
- `POST /api/tts` - Generate text-to-speech audio

## 🌐 Monitoring

The application includes Spring Boot Actuator endpoints:
- Health check: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Info: `http://localhost:8080/actuator/info`

## 📝 License

This project is licensed under the terms found in the LICENSE file.
