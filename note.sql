/*
SQLyog Ultimate v13.1.1 (64 bit)
MySQL - 8.0.46-0ubuntu0.22.04.3 : Database - note_backbond
*********************************************************************
*/

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`note_backbond` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `note_backbond`;

/*Table structure for table `note` */

DROP TABLE IF EXISTS `note`;

CREATE TABLE `note` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '笔记ID',
  `user_id` int NOT NULL COMMENT '所属用户ID',
  `notebook_id` int DEFAULT NULL COMMENT '所属笔记本ID（可为空）',
  `title` varchar(200) DEFAULT 'Untitled' COMMENT '笔记标题',
  `summary` varchar(500) DEFAULT NULL COMMENT '笔记摘要',
  `is_marked` tinyint(1) DEFAULT '0' COMMENT '是否星标(0=否 1=是)',
  `is_delete` tinyint(1) DEFAULT '0' COMMENT '是否删除(0=否 1=是)',
  `sort_order` int DEFAULT '0' COMMENT '排序序号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '删除时间',
  `version` int NOT NULL DEFAULT '1' COMMENT '版本号',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_notebook_id` (`notebook_id`),
  KEY `idx_is_delete` (`is_delete`),
  KEY `idx_is_marked` (`is_marked`),
  KEY `idx_userId_sortId_updatedAt` (`user_id`,`sort_order`,`updated_at` DESC),
  CONSTRAINT `fk_note_notebook` FOREIGN KEY (`notebook_id`) REFERENCES `notebook` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_note_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=222 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='笔记表';

/*Data for the table `note` */

insert  into `note`(`id`,`user_id`,`notebook_id`,`title`,`summary`,`is_marked`,`is_delete`,`sort_order`,`created_at`,`updated_at`,`deleted_at`,`version`) values 
(200,1,NULL,'123123','无摘要',0,0,0,'2026-07-30 16:54:49','2026-07-30 16:54:49',NULL,1),
(201,1,NULL,'1321312','无摘要',0,0,0,'2026-07-30 17:01:45','2026-07-30 17:01:45',NULL,1),
(202,1,NULL,'测试','无摘要',0,0,0,'2026-07-30 17:13:25','2026-07-30 17:13:25',NULL,1),
(203,1,NULL,'测试','无摘要',0,0,0,'2026-07-30 17:15:37','2026-07-30 17:15:37',NULL,1),
(204,1,NULL,'123213213','无摘要',0,0,0,'2026-07-30 17:17:21','2026-07-30 17:17:21',NULL,1),
(205,1,NULL,'1231231231','无摘要',0,0,0,'2026-07-30 17:19:10','2026-07-30 17:19:10',NULL,1),
(206,1,NULL,'12312312','无摘要',0,0,0,'2026-07-30 17:29:11','2026-07-30 17:29:11',NULL,1),
(207,1,NULL,'112213','无摘要',0,0,0,'2026-07-30 17:30:38','2026-07-30 17:30:38',NULL,1),
(208,1,NULL,'1231231','无摘要',0,0,0,'2026-07-30 17:33:12','2026-07-30 17:33:12',NULL,1),
(209,1,NULL,'131231231231','无摘要',0,0,0,'2026-07-30 17:44:38','2026-07-30 17:44:38',NULL,1),
(210,1,NULL,'123213','无摘要',0,0,0,'2026-07-30 17:48:39','2026-07-30 17:48:39',NULL,1),
(211,1,NULL,'123123','无摘要测试摘要',0,0,0,'2026-07-30 21:15:55','2026-07-30 21:16:13',NULL,2),
(212,1,NULL,'234234','无摘要',0,0,0,'2026-07-31 09:29:32','2026-07-31 09:29:32',NULL,1),
(213,1,NULL,'1113333445556666777','无摘要',0,0,0,'2026-07-31 09:29:52','2026-07-31 09:29:52',NULL,1),
(214,1,NULL,'2113123','无摘要',0,0,0,'2026-07-31 15:03:56','2026-07-31 15:03:56',NULL,1),
(215,1,NULL,'2132131','无摘要',0,0,0,'2026-07-31 15:20:56','2026-07-31 15:20:56',NULL,1),
(216,1,NULL,'123123','无摘要',0,0,0,'2026-07-31 15:21:19','2026-07-31 15:21:19',NULL,1),
(217,1,NULL,'123123====','无摘要',0,0,0,'2026-07-31 15:27:44','2026-07-31 15:27:44',NULL,1),
(218,1,4,'册饿请问','无摘要',0,0,0,'2026-07-31 15:28:52','2026-07-31 15:28:52',NULL,1),
(219,1,4,'阿三大苏打','无摘要',0,0,0,'2026-07-31 15:29:05','2026-07-31 15:29:05',NULL,1),
(220,1,4,'123123123213123','无摘要',0,0,0,'2026-07-31 15:29:15','2026-07-31 15:29:15',NULL,1);

/*Table structure for table `note_block` */

DROP TABLE IF EXISTS `note_block`;

CREATE TABLE `note_block` (
  `id` varchar(36) NOT NULL COMMENT '块id',
  `note_id` int NOT NULL COMMENT '所属笔记的id',
  `type` varchar(32) DEFAULT NULL COMMENT '块语义类型',
  `order_key` varchar(32) NOT NULL COMMENT '块索引',
  `delta_json` text COMMENT '块源json文本',
  `version` int(10) unsigned zerofill NOT NULL DEFAULT '0000000001' COMMENT '块版本号，乐观锁',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '块创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '块更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_noteid_orderkey` (`note_id`,`order_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

/*Data for the table `note_block` */

insert  into `note_block`(`id`,`note_id`,`type`,`order_key`,`delta_json`,`version`,`created_at`,`updated_at`) values 
('0124c02c-d74b-4851-a19d-f1d0e9e629d1',216,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"3123123123\"}]}}',0000000001,'2026-07-31 15:21:18','2026-07-31 15:21:18'),
('01a289b0-ea25-4f79-afa2-3f258d2a6803',213,'paragraph','zzzzzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"11\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('03999492-2216-4905-8607-add5d86d8182',210,'paragraph','y','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"3123\"}]}}',0000000001,'2026-07-30 17:48:39','2026-07-30 17:48:39'),
('0443ad85-55ac-4cf9-8639-dbf2f1345347',213,'paragraph','zzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('06c31131-3914-4ef4-81ac-45b39aa9992a',219,'paragraph','zy','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"21\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('0df251de-1225-451b-8aa0-96c93c31e3f0',213,'paragraph','zzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('0eed8cf6-1bdd-4c77-9b06-9c7718767084',213,'paragraph','zzzzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('1858b7b9-fc7e-40f9-bc26-907a1cb1b051',213,'paragraph','zzzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('1ab48755-d9eb-4a98-8e0e-963d98262a8f',213,'paragraph','zzzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('1d4025e8-bc22-4dfa-8c61-a241c4b30cff',219,'paragraph','t','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"12\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('23033820-8755-47c9-969f-4fce4f18a73b',213,'paragraph','zzzzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('28444eb7-a299-46d4-88cf-430a4769bdb2',212,'paragraph','zzzzy','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"234234234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('2f40915b-b4ac-4d26-a22c-4b7b4452e6a0',217,'paragraph','t','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1312\"}]}}',0000000001,'2026-07-31 15:27:44','2026-07-31 15:27:44'),
('30cb86f2-25b3-4963-af02-10e078bdaa4f',212,'paragraph','zz','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"23\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('30d5601a-5ad0-4ce0-8270-acd8f1dcab0e',212,'paragraph','zzz','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"423423\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('32c3dcce-ad7d-47de-8e38-f5fa767a7160',219,'paragraph','zz','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"312\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('33ff7469-a81f-4086-9f55-f6b82378a89d',213,'paragraph','zt','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('36078a76-7739-47f3-86b6-318349db59a7',213,'paragraph','zz','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('38215ecc-563a-4c4a-ab18-e7705bef32a2',212,'paragraph','zn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"423\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('3d7d4b84-3ede-4ac6-97df-7bf0b329738e',220,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"13213123213123\"}]}}',0000000001,'2026-07-31 15:29:15','2026-07-31 15:29:15'),
('3f57423e-1d99-4c57-8d70-993f62ee9851',212,'paragraph','zzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('4191d4b7-f0f1-42e2-b2d0-298a56100b7b',212,'paragraph','t','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"234234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('4b4c7cb1-91ca-4257-a24f-2584301206de',219,'paragraph','zw','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('4f519a37-a0ca-478d-a6e7-152c88fd6576',213,'paragraph','zzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('5b181805-789b-41ab-8480-705bac2dff20',212,'paragraph','y','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"2\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('617dddcc-784a-4585-b12f-18c5edcd4903',212,'paragraph','zzy','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"23\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('677ea6bc-0ec6-473c-9e10-f4a909df4ca0',212,'paragraph','zw','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"223\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('6fb0c46e-2901-42f6-8e0f-85e406431ccf',219,'paragraph','zn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"3\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('735a1e0b-90ab-4ddb-a242-87d50f6c084a',213,'paragraph','y','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('74e90dd2-7fbe-4f54-a179-7236ab218a33',219,'paragraph','zzy','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('75dabc8d-dbdb-4243-89ce-ef3eedfba176',214,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"12312313131231231\"}]}}',0000000001,'2026-07-31 15:03:55','2026-07-31 15:03:55'),
('77d9b14f-5c19-457d-bc14-798057a2b3d3',213,'paragraph','zn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('7bff6a68-0f2f-44c2-8c44-b4801ffcbcab',212,'paragraph','zzzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"42342\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('80e4c451-3f6e-4d0e-8c37-ac2771386b29',213,'paragraph','zzzzzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"111\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('817d14f2-34d6-465e-9309-8cc70568c22b',212,'paragraph','zzzz','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"23\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('821c2f1b-1fdd-45ef-961d-ae52290884fe',211,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1231231231231\"}]}}',0000000001,'2026-07-30 21:15:55','2026-07-30 21:15:55'),
('8376d0d6-4b7b-479a-aafc-14d5168c7547',213,'paragraph','zy','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('855a55e6-e1af-4226-92be-24e09edd28f8',212,'paragraph','zzzy','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"24\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('8c276eb1-4544-42d3-898e-3c582c228173',213,'paragraph','zzzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('8c3314b5-e6b8-4b52-8c00-5534570c8e8d',210,'paragraph','t','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123213\"}]}}',0000000001,'2026-07-30 17:48:39','2026-07-30 17:48:39'),
('8d1c2501-f90e-4461-9c6f-a04be2f0f21e',213,'paragraph','zzzzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('93046d9b-20ad-44b1-865c-6da335fca755',213,'paragraph','zzzzy','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('975a408e-a42e-412f-b224-c484914c3dc6',212,'paragraph','zzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"4\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('97fd1ff5-8734-4392-a8da-26ba175447f3',212,'paragraph','zzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('9a6e1b9b-2d6c-4053-89d4-cc29f1eb4309',212,'paragraph','zzzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('9b977ab3-b2bf-42fd-9509-559e19c52c27',213,'paragraph','zzzzzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('9bd5fe73-d1e6-458d-841f-e778d10e5861',214,'paragraph','w','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123213\"}]}}',0000000001,'2026-07-31 15:03:55','2026-07-31 15:03:55'),
('9de79883-7844-4c1f-ad27-a70c6cb31f22',212,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"42342424234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('9f987cd3-3bef-448c-b4c1-c12941d9f40e',213,'paragraph','zzzz','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('a2979eef-1d7b-4b32-8be7-019fd1026813',214,'paragraph','t','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1312312\"}]}}',0000000001,'2026-07-31 15:03:55','2026-07-31 15:03:55'),
('a42b08c5-657c-4cda-9ba0-129d83b8fdc4',213,'paragraph','zw','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('a4f41c93-ca63-4f34-9d52-abfbc86318d2',212,'paragraph','z','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"423\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('a5d4d0fa-42bc-4961-96fc-935cc5b619d0',213,'paragraph','zzzzz','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('a618dad2-e3f0-434e-a625-5844dbd52b8c',213,'paragraph','w','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('ab8ef1c1-8ff8-4c15-9563-94eabe269fbe',210,'paragraph','w','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"12321312\"}]}}',0000000001,'2026-07-30 17:48:39','2026-07-30 17:48:39'),
('abd55321-8675-466f-9b7c-c6759b8c23aa',219,'paragraph','zt','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('b0118453-2ae6-4942-baad-a08d4f7bc998',218,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"12312312312321\"}]}}',0000000001,'2026-07-31 15:28:52','2026-07-31 15:28:52'),
('b8205e99-05c3-4923-bada-3cdb9e5d10fd',213,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"1231231231231\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('b9d8ab30-901f-4d10-8d8a-d559984a9f48',219,'paragraph','z','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"21\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('bd778fff-8131-42c4-acf0-ecd01ba5b7e8',213,'paragraph','t','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123123\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('c12822e2-1abd-4acc-8d88-2086b89b021d',219,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123213123123\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('c4a96288-cdb9-4796-8f79-cab403a9b03f',213,'paragraph','zzzy','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('c972becb-22e3-4fd6-9fc1-0fbe8e68bfa5',219,'paragraph','w','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"3\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('cba02bc9-47d3-4327-9368-2b02a272aab9',212,'paragraph','zt','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"4\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('cbe71c62-9109-4ede-b391-ef1374c02b75',217,'paragraph','w','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"12312312\"}]}}',0000000001,'2026-07-31 15:27:44','2026-07-31 15:27:44'),
('db1318a5-61e7-4bdb-baf3-8bc6b6fb66ec',212,'paragraph','zy','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"4234234242\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('dd10b2ac-ff18-471c-9a67-76c62655f0c9',212,'paragraph','zzzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"34\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('de18b660-92bd-46ed-b2b5-e7a7a1fefcd7',210,'paragraph','z','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"13123\"}]}}',0000000001,'2026-07-30 17:48:39','2026-07-30 17:48:39'),
('e04f4d8c-5098-408d-9977-725a2aaac025',213,'paragraph','zzz','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('e0d427cd-8a0b-4a4b-a25e-fd29e406760c',219,'paragraph','y','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('e46bbe0c-0cc0-44cb-acc1-bad8aa954dc1',215,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"3123123123123213\"}]}}',0000000001,'2026-07-31 15:20:56','2026-07-31 15:20:56'),
('e7288b64-7f98-41f8-bdbb-2358351b310e',219,'paragraph','zzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"3\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('ed010b00-bddd-4761-afcf-dbc572bff8c8',219,'paragraph','zzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123\"}]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04'),
('ee7c5523-a5cd-48b8-b810-f59e06a20d5d',212,'paragraph','w','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('efe3f041-a20d-4c12-ae06-043e7123fee9',217,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"12312312312312123123213\"}]}}',0000000001,'2026-07-31 15:27:44','2026-07-31 15:27:44'),
('f304bd0c-bbdb-4740-9e27-7ee851121513',213,'paragraph','z','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"11\"}]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('f6137b7e-fbaa-4192-873d-d5f2d3ef676d',213,'paragraph','zzy','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 09:29:52','2026-07-31 09:29:52'),
('f7ffaf4d-639f-41c3-86c1-215948096dc7',212,'paragraph','zzzzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"234\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('f8de2cb9-981a-436d-9ac4-70a03e3f304a',212,'paragraph','zzzzt','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"424\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('f9726b20-fbc9-4c88-b8a0-6616159e7a69',210,'paragraph','n','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"123123123213123\"}]}}',0000000001,'2026-07-30 17:48:39','2026-07-30 17:48:39'),
('faa4428c-bf1c-4669-9903-2c15bdcce3f2',212,'paragraph','zzzzn','{\"type\":\"paragraph\",\"data\":{\"delta\":[{\"insert\":\"423\"}]}}',0000000001,'2026-07-31 09:29:31','2026-07-31 09:29:31'),
('fce0da43-edb8-4bd9-85bc-478a94d208d6',219,'paragraph','zzw','{\"type\":\"paragraph\",\"data\":{\"delta\":[]}}',0000000001,'2026-07-31 15:29:04','2026-07-31 15:29:04');

/*Table structure for table `note_tag` */

DROP TABLE IF EXISTS `note_tag`;

CREATE TABLE `note_tag` (
  `note_id` int NOT NULL COMMENT '笔记ID',
  `tag_id` int NOT NULL COMMENT '标签ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `note_id` (`note_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  KEY `idx_notedId_updated_tagId` (`note_id`,`updated_at`,`tag_id`) COMMENT '查询某个笔记下的所有tag，按照事件排序',
  CONSTRAINT `fk_nt_note` FOREIGN KEY (`note_id`) REFERENCES `note` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_nt_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='笔记-标签关联表';

/*Data for the table `note_tag` */

insert  into `note_tag`(`note_id`,`tag_id`,`updated_at`) values 
(215,3,'2026-07-31 15:21:03'),
(215,5,'2026-07-31 15:21:03'),
(215,6,'2026-07-31 15:21:03'),
(216,17,'2026-07-31 15:21:29'),
(216,20,'2026-07-31 15:21:29'),
(216,22,'2026-07-31 15:21:29'),
(216,24,'2026-07-31 15:21:29'),
(216,25,'2026-07-31 15:21:29'),
(216,28,'2026-07-31 15:21:29'),
(216,29,'2026-07-31 15:21:29'),
(217,17,'2026-07-31 15:27:57'),
(217,20,'2026-07-31 15:27:57'),
(217,21,'2026-07-31 15:27:57'),
(217,22,'2026-07-31 15:27:57'),
(217,24,'2026-07-31 15:27:57'),
(220,1,'2026-07-31 15:29:26'),
(220,2,'2026-07-31 15:29:26'),
(220,9,'2026-07-31 15:29:26'),
(220,10,'2026-07-31 15:29:26'),
(220,17,'2026-07-31 15:29:26'),
(220,20,'2026-07-31 15:29:26'),
(220,21,'2026-07-31 15:29:26'),
(220,22,'2026-07-31 15:29:26'),
(220,23,'2026-07-31 15:29:26'),
(220,24,'2026-07-31 15:29:26'),
(220,25,'2026-07-31 15:29:26'),
(220,28,'2026-07-31 15:29:26'),
(220,29,'2026-07-31 15:29:26'),
(220,30,'2026-07-31 15:29:26'),
(220,3,'2026-07-31 15:29:37'),
(220,4,'2026-07-31 15:29:37'),
(220,5,'2026-07-31 15:29:37'),
(220,6,'2026-07-31 15:29:37'),
(220,7,'2026-07-31 15:29:37'),
(220,8,'2026-07-31 15:29:37'),
(220,11,'2026-07-31 15:29:37'),
(220,12,'2026-07-31 15:29:37');

/*Table structure for table `notebook` */

DROP TABLE IF EXISTS `notebook`;

CREATE TABLE `notebook` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '笔记本ID',
  `user_id` int NOT NULL COMMENT '所属用户ID',
  `name` varchar(100) NOT NULL COMMENT '笔记本名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `color` varchar(20) DEFAULT NULL COMMENT '显示颜色',
  `sort_order` int DEFAULT '0' COMMENT '排序序号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `avatar` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '头像的url',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_notebook_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='笔记本表';

/*Data for the table `notebook` */

insert  into `notebook`(`id`,`user_id`,`name`,`description`,`color`,`sort_order`,`created_at`,`updated_at`,`avatar`) values 
(1,1,'数据库','',NULL,0,'2026-07-13 12:39:06','2026-07-13 15:39:58','test.icon'),
(2,1,'大模型','',NULL,0,'2026-07-14 13:47:12','2026-07-14 13:47:12',NULL),
(3,1,'人文','12321312',NULL,0,'2026-07-26 21:20:03','2026-07-26 21:20:03',NULL),
(4,1,'测试笔记本','',NULL,0,'2026-07-27 11:06:13','2026-07-27 11:06:13',NULL);

/*Table structure for table `tag` */

DROP TABLE IF EXISTS `tag`;

CREATE TABLE `tag` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `user_id` int NOT NULL COMMENT '所属用户ID',
  `name` varchar(50) NOT NULL COMMENT '标签名称',
  `color` varchar(20) DEFAULT NULL COMMENT '标签颜色',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag` (`user_id`,`name`),
  CONSTRAINT `fk_tag_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签表';

/*Data for the table `tag` */

insert  into `tag`(`id`,`user_id`,`name`,`color`,`created_at`) values 
(1,1,'学习','red','2026-07-13 16:58:03'),
(2,1,'日常记录','blue','2026-07-13 16:58:20'),
(3,1,'测试',NULL,'2026-07-30 15:49:40'),
(4,1,'22222',NULL,'2026-07-30 15:50:48'),
(5,1,'233333',NULL,'2026-07-30 15:50:58'),
(6,1,'测试打的',NULL,'2026-07-30 15:57:51'),
(7,1,'方法',NULL,'2026-07-30 16:02:20'),
(8,1,'顶顶顶顶',NULL,'2026-07-30 16:03:34'),
(9,1,'时代的',NULL,'2026-07-30 16:07:06'),
(10,1,'得到的',NULL,'2026-07-30 16:09:32'),
(11,1,'11111',NULL,'2026-07-30 16:14:25'),
(12,1,'13123123213',NULL,'2026-07-30 16:19:00'),
(17,1,'测试2','#ff9800','2026-07-30 16:40:28'),
(20,1,'滴滴答答','#f44336','2026-07-30 16:41:28'),
(21,1,'22222222','#4caf50','2026-07-30 16:45:59'),
(22,1,'大顶堆','#ff9800','2026-07-30 16:47:35'),
(23,1,'大大大','#2196f3','2026-07-30 16:48:20'),
(24,1,'等待','#9c27b0','2026-07-30 16:49:56'),
(25,1,'23123131','#2196f3','2026-07-30 17:08:43'),
(28,1,'11222333','#ffeb3b','2026-07-30 17:17:35'),
(29,1,'改改改','#4caf50','2026-07-30 17:19:23'),
(30,1,'得到的11','#4caf50','2026-07-30 17:21:00');

/*Table structure for table `user` */

DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `account` varchar(50) NOT NULL COMMENT '账号（唯一）',
  `password` varchar(255) NOT NULL COMMENT '密码（加密存储）',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
  `phone_number` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_account` (`account`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

/*Data for the table `user` */

insert  into `user`(`id`,`account`,`password`,`nickname`,`email`,`phone_number`,`avatar`) values 
(1,'test','$2a$10$u6GmvWDxEXCd5fgrvjxBR.T/.SMFmdji8fweAfb12jJxaC.b4EJN.','test','headphone233@163.com','19863832733',NULL);

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
