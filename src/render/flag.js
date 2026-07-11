// Team flag: a colored banner on a pale pole, waving via per-frame vertex
// displacement. Each flag has a stand + light beacon at its team's base
// (beacon on = flag is home = your team can score). Dropped flags bob and
// pulse "come get me"; carried flags lean onto the carrier's back. The flag
// body is a physics object in the sim — this view just follows it.

import * as THREE from 'three';
import { toonMat, toonGradient } from './characters.js';
import { TEAMS } from '../core/config.js';
import { lerp, clamp } from '../core/math.js';

export class FlagView {
  constructor(scene, level, team) {
    this.scene = scene;
    this.level = level;
    this.team = team;
    const colors = TEAMS[team];

    this.group = new THREE.Group();
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.05, 1.75, 8), toonMat('#e8e4d8'));
    pole.position.y = 0.875;
    pole.castShadow = true;
    const topper = new THREE.Mesh(
      new THREE.SphereGeometry(0.09, 10, 8),
      new THREE.MeshBasicMaterial({ color: '#ffe27a' }),
    );
    topper.position.y = 1.8;

    this.clothGeo = new THREE.PlaneGeometry(1.05, 0.62, 10, 6);
    this.clothGeo.translate(0.525, 0, 0); // hang off the pole's right side
    this.basePos = Float32Array.from(this.clothGeo.attributes.position.array);
    this.cloth = new THREE.Mesh(
      this.clothGeo,
      new THREE.MeshToonMaterial({
        color: colors.color,
        emissive: '#000000',
        side: THREE.DoubleSide,
        gradientMap: toonGradient(),
      }),
    );
    this.cloth.position.set(0.045, 1.42, 0);
    this.cloth.castShadow = true;
    // emblem: dark bomb roundel on the cloth
    const emblem = new THREE.Mesh(
      new THREE.CircleGeometry(0.14, 16),
      new THREE.MeshBasicMaterial({ color: '#20232c', side: THREE.DoubleSide }),
    );
    emblem.position.set(0.45, 1.42, 0.01);
    this.group.add(pole, topper, this.cloth, emblem);
    scene.add(this.group);

    // stand + beacon at this team's base
    const stand = level.flags[team];
    this.dais = new THREE.Group();
    const plate = new THREE.Mesh(new THREE.CylinderGeometry(1.0, 1.15, 0.12, 24), toonMat('#5a6474'));
    plate.position.y = 0.06;
    plate.receiveShadow = true;
    const trim = new THREE.Mesh(
      new THREE.TorusGeometry(1.0, 0.05, 8, 30),
      new THREE.MeshBasicMaterial({ color: colors.glow }),
    );
    trim.rotation.x = Math.PI / 2;
    trim.position.y = 0.12;
    this.beam = new THREE.Mesh(
      new THREE.CylinderGeometry(0.5, 0.68, 9, 16, 1, true),
      new THREE.MeshBasicMaterial({
        color: colors.glow,
        transparent: true,
        opacity: 0.13,
        blending: THREE.AdditiveBlending,
        side: THREE.DoubleSide,
        depthWrite: false,
      }),
    );
    this.beam.position.y = 4.5;
    this.dais.add(plate, trim, this.beam);
    this.dais.position.set(stand.x, 0, stand.z);
    scene.add(this.dais);

    this.tilt = 0;
  }

  update(f, dt, time) {
    if (!f) return;
    const carried = f.st === 'carry';
    const dropped = f.st === 'drop';

    this.tilt = clamp(this.tilt + (carried ? 4 : -4) * dt, 0, 1);
    const bob = dropped && f.y <= 0.05 ? Math.abs(Math.sin(time * 3.2)) * 0.1 : 0;
    this.group.position.set(f.x, f.y + (carried ? 0.45 : 0.12) + bob, f.z);
    this.group.rotation.x = lerp(0, -0.55, this.tilt); // lean back when strapped on
    // in flight it pitches with its arc; at rest / home it stays upright
    const flying = !carried && f.y > 0.1;
    this.group.rotation.z = flying ? clamp(-f.vx * 0.03, -0.4, 0.4) : 0;
    this.group.rotation.y = carried ? 0 : Math.sin(time * 0.6) * 0.4;

    // wave the cloth: amplitude grows toward the free edge, faster when moving
    const speed2 = Math.hypot(f.vx ?? 0, f.vz ?? 0);
    const pos = this.clothGeo.attributes.position;
    const speed = carried || speed2 > 2 ? 11 : 6.5;
    const amp = carried || speed2 > 2 ? 0.13 : 0.09;
    for (let i = 0; i < pos.count; i++) {
      const bx = this.basePos[i * 3];
      const by = this.basePos[i * 3 + 1];
      const edge = bx / 1.05;
      pos.setZ(i, (Math.sin(bx * 5.5 - time * speed) * amp + Math.sin(bx * 11 + by * 4 - time * speed * 1.6) * amp * 0.35) * edge);
    }
    pos.needsUpdate = true;
    this.clothGeo.computeVertexNormals();

    // beacon only glows while this flag is home (= its team can score)
    this.beam.visible = f.st === 'home';
    this.beam.material.opacity = 0.11 + Math.sin(time * 2) * 0.04;
    const urgent = dropped ? (Math.sin(time * ((f.idle ?? 0) > 24 ? 14 : 6)) + 1) / 2 : 0;
    this.cloth.material.emissive.setScalar(urgent * 0.3);
  }

  dispose() {
    this.scene.remove(this.group);
    this.scene.remove(this.dais);
  }
}
