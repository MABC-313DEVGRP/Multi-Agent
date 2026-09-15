<img src=https://github.com/user-attachments/assets/324262e8-01c7-4519-a9df-f2c3e3d0d15b>

# 📜 A-RMS is.
> **A**LM<sup>( Jira, Redmine, GitLab... )</sup> integrated **R**equirement Base Project **M**anagement **S**ystem ( with. AI )
> <br>요구사항을 기반한 ALM 통합하고 인공지능을 내장한 PMS

# 📚 Documentation
> ⚠️ 313DEVGRP 는 개발 문서의 중요성을 매우 높게 생각하고 있습니다. <br>
> Project Charter 와 소프트웨어 요구사항 명세서(Software Requirement Specification) <br>
> 소프트웨어 설계 명세서(Software Design Specification) 는 표준 문서로 활용되고 있으며<br>
> 이외 사용자 메뉴얼(User Manual) 과 QA Checklist 까지 제품에 내장되어야 한다고 믿습니다.<br>
> 우리는 노션 스타일의 Wiki를 A-RMS 내부에 내장하여 상기 문서를 서비스 하기로 했습니다.
> 
다음의 링크를 참조하세요 : [**A-DOC WIKI By 313DEVGRP**](https://a-rms.net/adoc)


## 🦮 Setup

A-RMS는 MSA 모듈 구조를 활용했습니다. 따라서, Docker Base 로 Artifact를 운영하며,<br>
각 모듈을 Build 시 Dev profile 을 활용하며, Docker Image 는 Nexus 를 활용하여 <br>
Docker Swarm 및 Kubernetes 를 기반으로 운영합니다.

* 🐳 A-RMS 는 Docker 를 지향합니다.
    * ✔️ Docker Swarm Cluster 를 통하여, OnPremise 환경을 지원하며,
    * ✔️ Kubernetes 를 통하여, Cloud 환경을 지원하며, 멀티 테넌시를 활용한 SaaS 서비스를 제공합니다.
    * ✔️ 하기 Repository 중 Java-Service-Tree-Framework-IaC-System 를 참조하시면 Cluster Deploy Script 를 확인 하실 수 있습니다. 
* 🔧 개발환경에 관한 내용은 아래를 참조 부탁드립니다.
    * ✔️ Spring Profile 은 dev 로 고정하며, 각 MSA 모듈의 Root Path에는 개발규칙.txt 가 존재합니다.
    * ✔️ 또한, README.md 파일을 통해서 필요한 패키지 정보와 함께 아키텍쳐를 구성할 수 있도록 지원했습니다.
    * ✔️ 313DEVGRP 는 기술 스택은 쌓이는 것이라 생각합니다. 따라서, PLE 아키텍쳐 기반으로 MSA 모듈의 특징별로 Service Framework 를 구현했습니다.

## 🧬 PLE Architecture
313DEVGRP 는 PLE 아키텍쳐를 지향합니다. 따라서, 하나의 아키텍쳐와 알고리즘으로 다양한 제품과 솔루션에 대응하는 방법을 고민합니다.

<img src=https://github.com/user-attachments/assets/1f91bc84-95e4-4fbd-be91-e7eed47a59b5>

## 🧩 Plugins

A-RMS는 다양한 오픈소스 플러그인을 활용합니다. 특히나 Spring 의 경우는 Cloud Framework 의 모든 플러그인을 활용하고 있으며,<br>
추가로 활용되는 Frontend 의 플러그인은 custermize 하여 A-RMS와 integration 합니다.<br>
아래는 활용되는 오픈소스의 라이선스를 기재하며, 라이선스 요구를 준수하기 위하여 노력합니다.

다음의 링크를 참조하세요 : [**DEVTOOLS By 313DEVGRP**](https://a-rms.net/arms/template.html?page=community_devtools)

<img src=https://github.com/user-attachments/assets/e4f47695-2c5d-4906-8a87-c5b3814909fb>

## 💪 Contributing

우리 A-RMS 팀은 외부 컨트리뷰션에 관대합니다.<br>
단, A-RMS 가 지향하는 목적과 부합하는 기술적 활용에 대한 컨트리뷰션이어야 하며,<br>
A-RMS 팀은 Project Management 의 Scope Creep 을 경계하고 있습니다.<br>
다음으로 컨트리뷰션에 대한 요청을 보내주십시오. 함께하길 희망합니다. ( mailto : 313cokr@gmail.com )

<img src=https://github.com/user-attachments/assets/46304f50-5a79-4db3-86fc-cd935e58dbff>

## 📜 License

```
MIT License
Copyright 2012-present (c)313DEVGRP
```
